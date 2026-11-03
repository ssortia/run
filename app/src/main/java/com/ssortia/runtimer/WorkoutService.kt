package com.ssortia.runtimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Ведёт тренировку при выключенном экране. Цикл и все методы сервиса работают в главном потоке,
 * поэтому после [onDestroy] цикл уже не может подать сигнал или обновить уведомление.
 *
 * Завершение одно на все пути: «Стоп» вызывает stopService(), в том числе во время отсчёта,
 * финал вызывает stopSelf(), ресурсы освобождает только [onDestroy]. Единственное исключение:
 * на паузе wake lock снимается временно и берётся заново при продолжении.
 *
 * Команды паузы приходят обычными интентами через startService() и безопасны при повторе.
 */
class WorkoutService : Service() {
    private val scope = MainScope()

    // Остаётся заполненным и после финала: до onDestroy новый запрос не должен запустить тренировку
    private var job: Job? = null
    private var beeper: Beeper? = null
    // null, если вибрация выключена в настройках
    private var buzzer: Buzzer? = null
    // «Приглушать музыку заранее»: поле, потому что её читает и handlePauseAction
    private var duckAhead = DEFAULT_DUCK_AHEAD
    // Создаётся после отсчёта, команда паузы может прийти раньше
    private var clock: WorkoutClock? = null
    // Для записи в истории. Оба заполнены, если clock не null: workout при старте,
    // runStartedAtMs (системное время начала бега) перед созданием clock
    private lateinit var workout: Workout
    private var runStartedAtMs = 0L
    private var shownText: String? = null

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false) }
    }
    private val openAppIntent by lazy {
        val launchIntent = checkNotNull(packageManager.getLaunchIntentForPackage(packageName))
        PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // После startForegroundService() вызов обязателен, даже если запрос будет отклонён
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            // Текущий текст, а не заглушка: цикл обновляет уведомление только при смене текста,
            // а на паузе он не меняется
            buildNotification(shownText ?: getString(R.string.app_name)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (job != null) {
            handlePauseAction(intent?.action)
            return START_NOT_STICKY
        }

        val workout = intent?.toWorkout()
        if (workout == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startWorkout(workout)
        return START_NOT_STICKY
    }

    private fun handlePauseAction(action: String?) {
        val clock = clock ?: return
        val now = SystemClock.elapsedRealtime()
        when (action) {
            ACTION_PAUSE -> {
                // В финальном хвосте часы паузу не примут: снятие wake lock
                // растянуло бы ожидание конца сигнала
                clock.pause(now)
                if (clock.isPaused) {
                    // Сразу, а не на следующем шаге цикла: без wake lock он может не проснуться,
                    // и музыка осталась бы приглушённой на всю паузу
                    beeper?.stopDucking()
                    // На паузе процессору не над чем работать, а таймаут истёк бы на долгой паузе
                    if (wakeLock.isHeld) wakeLock.release()
                }
            }
            ACTION_RESUME -> if (clock.isPaused) {
                clock.resume(now)
                wakeLock.acquire(wakeLockTimeoutMs(clock.remainingAt(now), delayMs = 0))
                // На следующем шаге цикла приглушать было бы уже поздно
                val elapsedMs = clock.elapsedAt(now)
                if (shouldDuckAfterResume(workout, elapsedMs, duckAhead = duckAhead)) beeper?.duck()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        // После отмены цикла часы и поля уже не меняются
        saveRun()
        beeper?.release()
        buzzer?.release()
        if (wakeLock.isHeld) wakeLock.release()
        // Шаг цикла из очереди мог вернуть уведомление уже после того, как stopService() его снял
        notificationManager.cancel(NOTIFICATION_ID)
        _state.value = WorkoutState.Idle
        super.onDestroy()
    }

    // Без часов бег не начался: отклонённый запрос, команда паузы или «Стоп» во время отсчёта
    private fun saveRun() {
        val clock = clock ?: return
        val record = runRecordOrNull(
            workout,
            startedAtMs = runStartedAtMs,
            elapsedMs = clock.elapsedAt(SystemClock.elapsedRealtime()),
        ) ?: return
        // Сбой записи не должен прервать onDestroy: иначе ресурсы не освободятся и Idle не настанет
        try {
            RunHistory.save(this, record)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Не удалось сохранить пробежку", e)
        }
    }

    private fun startWorkout(workout: Workout) {
        this.workout = workout
        val settings = SettingsStore(this)
        val delayMs = settings.loadStartDelaySec() * 1000L
        wakeLock.acquire(wakeLockTimeoutMs(workout.totalMs, delayMs))
        beeper = Beeper(audioManager, resources, settings.loadSignalVolume())
        duckAhead = settings.loadDuckAhead()
        if (settings.loadVibration()) buzzer = Buzzer(getSystemService(Vibrator::class.java))
        job = scope.launch { runLoop(workout, delayMs) }
    }

    private suspend fun runLoop(workout: Workout, delayMs: Long) {
        // Момент старта считается один раз, поэтому шаги цикла и задержка не копят ошибку
        val startedAt = SystemClock.elapsedRealtime() + delayMs
        val soundFailed = beeper?.isReady != true
        if (delayMs > 0) {
            runCountdown(startedAt, soundFailed)
            // START идёт мимо tick.signal: всё, что реагирует на сигналы, нужно и здесь
            beeper?.play(Signal.START)
            buzzer?.play(Signal.START)
        }

        // Пересчёт к моменту startedAt: после отсчёта и сигнала START уже прошло немного времени.
        // До clock: saveRun считает поле заполненным, если часы есть
        runStartedAtMs = wallTimeAt(System.currentTimeMillis(), SystemClock.elapsedRealtime(), startedAt)
        // Создаётся после отсчёта: до старта прошедшее время было бы отрицательным
        val clock = WorkoutClock(workout.totalMs, startedAt).also { this.clock = it }
        var previousIndex: Int? = null

        while (true) {
            val elapsedMs = clock.elapsedAt(SystemClock.elapsedRealtime())
            val tick = tickAt(
                workout,
                elapsedMs,
                previousIndex,
                soundFailed = soundFailed,
                soundMuted = audioManager.isMediaMuted(),
                paused = clock.isPaused,
            )
            _state.value = tick.state
            showNotification(textFor(tick.state))

            tick.signal?.let {
                beeper?.play(it)
                buzzer?.play(it)
            }
            if (tick.finished) break
            // Каждый отрезок кончается сигналом: смена отрезка или финал
            val remainingMs = tick.state.position.remainingMs
            if (shouldDuck(remainingMs, duckAhead = duckAhead, paused = clock.isPaused)) {
                beeper?.duck()
            } else {
                beeper?.stopDucking()
            }
            previousIndex = tick.state.position.index
            delay(TICK_MS)
        }

        delay(finishWaitMs())
        stopSelf()
    }

    private suspend fun runCountdown(startedAt: Long, soundFailed: Boolean) {
        while (true) {
            val now = SystemClock.elapsedRealtime()
            val tick = countdownTickAt(
                startedAt,
                now,
                soundFailed = soundFailed,
                soundMuted = audioManager.isMediaMuted(),
            ) ?: break
            _state.value = tick.state
            // Отсчёт кончается сигналом START
            if (shouldDuck(startedAt - now, duckAhead = duckAhead)) beeper?.duck()
            showNotification(countdownText(tick.state))
            delay(tick.waitMs)
        }
    }

    // Сравнивается весь текст: при отрезках в 1 с секунды совпадают, а отрезок уже другой
    private fun showNotification(text: String) {
        if (text == shownText) return
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
        shownText = text
    }

    private fun textFor(active: WorkoutState.Active): String = notificationText(
        position = active.position,
        rounds = active.rounds,
        paused = active.paused,
        runLabel = getString(R.string.segment_run),
        walkLabel = getString(R.string.segment_walk),
        roundLabel = getString(R.string.round_label),
        pausedLabel = getString(R.string.notification_paused),
    )

    private fun countdownText(countdown: WorkoutState.Countdown): String = countdownNotificationText(
        seconds = countdown.seconds,
        countdownLabel = getString(R.string.countdown_label),
        secondsLabel = getString(R.string.seconds_short),
    )

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            // Текст нужен на заблокированном экране: телефон лежит в кармане или на руке
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun Intent.toWorkout(): Workout? = workoutOrNull(
        runSec = getIntExtra(EXTRA_RUN_SEC, 0),
        walkSec = getIntExtra(EXTRA_WALK_SEC, 0),
        rounds = getIntExtra(EXTRA_ROUNDS, 0),
    )

    companion object {
        private const val CHANNEL_ID = "workout"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG = "RunTimer:workout"
        private const val TAG = "WorkoutService"
        private const val EXTRA_RUN_SEC = "runSec"
        private const val EXTRA_WALK_SEC = "walkSec"
        private const val EXTRA_ROUNDS = "rounds"
        private const val ACTION_PAUSE = "com.ssortia.runtimer.action.PAUSE"
        private const val ACTION_RESUME = "com.ssortia.runtimer.action.RESUME"

        private val _state = MutableStateFlow<WorkoutState>(WorkoutState.Idle)
        val state: StateFlow<WorkoutState> = _state.asStateFlow()

        /** Интент для startForegroundService(). Останавливать сервис нужно через stopService(). */
        fun startIntent(context: Context, workout: Workout): Intent =
            Intent(context, WorkoutService::class.java)
                .putExtra(EXTRA_RUN_SEC, workout.runSec)
                .putExtra(EXTRA_WALK_SEC, workout.walkSec)
                .putExtra(EXTRA_ROUNDS, workout.rounds)

        /**
         * Команда паузы для startService(). Без активной тренировки сервис её отклонит, но если
         * экземпляр сервиса уже уничтожен, команда создаст новый, и тот до остановки успеет
         * показать уведомление «RunTimer».
         */
        fun pauseIntent(context: Context): Intent =
            Intent(context, WorkoutService::class.java).setAction(ACTION_PAUSE)

        /** Команда продолжения для startService(). Отклоняется так же, как [pauseIntent]. */
        fun resumeIntent(context: Context): Intent =
            Intent(context, WorkoutService::class.java).setAction(ACTION_RESUME)
    }
}
