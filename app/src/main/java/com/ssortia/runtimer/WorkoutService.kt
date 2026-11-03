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
    // Создаётся после отсчёта, команда паузы может прийти раньше
    private var clock: WorkoutClock? = null
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
            // Цикл обновляет уведомление только при смене текста, а на паузе текст не меняется
            buildNotification(
                (state.value as? WorkoutState.Active)?.let(::textFor) ?: getString(R.string.app_name),
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (job != null) {
            handlePauseAction(intent?.action)
            return START_NOT_STICKY
        }

        val workout = intent?.toWorkout()
        val delayMs = intent?.toStartDelayMs()
        if (workout == null || delayMs == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startWorkout(workout, delayMs)
        return START_NOT_STICKY
    }

    // Пауза в финальном хвосте игнорируется часами: время уже дошло до totalMs
    private fun handlePauseAction(action: String?) {
        val clock = clock ?: return
        val now = SystemClock.elapsedRealtime()
        when (action) {
            ACTION_PAUSE -> {
                clock.pause(now)
                // На паузе процессору не над чем работать, а таймаут истёк бы на долгой паузе
                if (clock.isPaused && wakeLock.isHeld) wakeLock.release()
            }
            ACTION_RESUME -> if (clock.isPaused) {
                clock.resume(now)
                wakeLock.acquire(clock.remainingAt(now) + WAKE_LOCK_MARGIN_MS)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        beeper?.release()
        if (wakeLock.isHeld) wakeLock.release()
        // Шаг цикла из очереди мог вернуть уведомление уже после того, как stopService() его снял
        notificationManager.cancel(NOTIFICATION_ID)
        _state.value = WorkoutState.Idle
        super.onDestroy()
    }

    private fun startWorkout(workout: Workout, delayMs: Long) {
        wakeLock.acquire(wakeLockTimeoutMs(workout.totalMs, delayMs))
        beeper = Beeper(audioManager, SettingsStore(this).loadSignalVolume())
        job = scope.launch { runLoop(workout, delayMs) }
    }

    private suspend fun runLoop(workout: Workout, delayMs: Long) {
        // Момент старта считается один раз, поэтому шаги цикла и задержка не копят ошибку
        val startedAt = SystemClock.elapsedRealtime() + delayMs
        val soundFailed = beeper?.isReady != true
        if (delayMs > 0) {
            runCountdown(startedAt, soundFailed)
            beeper?.play(Signal.START)
        }

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

            tick.signal?.let { beeper?.play(it) }
            if (tick.finished) break
            previousIndex = tick.state.position.index
            delay(TICK_MS)
        }

        delay(soundWithTailMs(Signal.FINISH))
        stopSelf()
    }

    private suspend fun runCountdown(startedAt: Long, soundFailed: Boolean) {
        while (true) {
            val tick = countdownTickAt(
                startedAt,
                SystemClock.elapsedRealtime(),
                soundFailed = soundFailed,
                soundMuted = audioManager.isMediaMuted(),
            ) ?: break
            _state.value = tick.state
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

    // Умолчание -1: интент без значения отклоняется, как и неверная тренировка
    private fun Intent.toStartDelayMs(): Long? =
        startDelayMsOrNull(getIntExtra(EXTRA_START_DELAY_SEC, -1))

    companion object {
        private const val CHANNEL_ID = "workout"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG = "RunTimer:workout"
        private const val EXTRA_RUN_SEC = "runSec"
        private const val EXTRA_WALK_SEC = "walkSec"
        private const val EXTRA_ROUNDS = "rounds"
        private const val EXTRA_START_DELAY_SEC = "startDelaySec"
        private const val ACTION_PAUSE = "com.ssortia.runtimer.action.PAUSE"
        private const val ACTION_RESUME = "com.ssortia.runtimer.action.RESUME"

        private val _state = MutableStateFlow<WorkoutState>(WorkoutState.Idle)
        val state: StateFlow<WorkoutState> = _state.asStateFlow()

        /** Интент для startForegroundService(). Останавливать сервис нужно через stopService(). */
        fun startIntent(context: Context, workout: Workout, startDelaySec: Int): Intent =
            Intent(context, WorkoutService::class.java)
                .putExtra(EXTRA_RUN_SEC, workout.runSec)
                .putExtra(EXTRA_WALK_SEC, workout.walkSec)
                .putExtra(EXTRA_ROUNDS, workout.rounds)
                .putExtra(EXTRA_START_DELAY_SEC, startDelaySec)

        /** Команда паузы для startService(). Без активной тренировки сервис её отклонит. */
        fun pauseIntent(context: Context): Intent =
            Intent(context, WorkoutService::class.java).setAction(ACTION_PAUSE)

        /** Команда продолжения для startService(). */
        fun resumeIntent(context: Context): Intent =
            Intent(context, WorkoutService::class.java).setAction(ACTION_RESUME)
    }
}
