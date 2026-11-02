package com.ssortia.runtimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
 * финал вызывает stopSelf(), ресурсы освобождает только [onDestroy].
 */
class WorkoutService : Service() {
    private val scope = MainScope()

    // Остаётся заполненным и после финала: до onDestroy новый запрос не должен запустить тренировку
    private var job: Job? = null
    private var beeper: Beeper? = null
    private var shownText: String? = null

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
            buildNotification(getString(R.string.app_name)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (job != null) return START_NOT_STICKY

        val workout = intent?.toWorkout()
        val delayMs = intent?.toStartDelayMs()
        if (workout == null || delayMs == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startWorkout(workout, delayMs)
        return START_NOT_STICKY
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
        beeper = Beeper()
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

        var previousIndex: Int? = null

        while (true) {
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            val tick = tickAt(workout, elapsedMs, previousIndex, soundFailed)
            _state.value = tick.state
            showNotification(textFor(tick.state))

            tick.signal?.let { beeper?.play(it) }
            if (tick.finished) break
            previousIndex = tick.state.position.index
            delay(TICK_MS)
        }

        // Звук выходит из динамика с задержкой, release() без запаса обрежет конец сигнала
        delay(soundDurationMs(Signal.FINISH) + FINISH_TAIL_MS)
        stopSelf()
    }

    private suspend fun runCountdown(startedAt: Long, soundFailed: Boolean) {
        while (true) {
            val tick = countdownTickAt(startedAt, SystemClock.elapsedRealtime(), soundFailed) ?: break
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
        runLabel = getString(R.string.segment_run),
        walkLabel = getString(R.string.segment_walk),
        roundLabel = getString(R.string.round_label),
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
        private const val FINISH_TAIL_MS = 300L
        private const val WAKE_LOCK_TAG = "RunTimer:workout"
        private const val EXTRA_RUN_SEC = "runSec"
        private const val EXTRA_WALK_SEC = "walkSec"
        private const val EXTRA_ROUNDS = "rounds"
        private const val EXTRA_START_DELAY_SEC = "startDelaySec"

        private val _state = MutableStateFlow<WorkoutState>(WorkoutState.Idle)
        val state: StateFlow<WorkoutState> = _state.asStateFlow()

        /** Интент для startForegroundService(). Останавливать сервис нужно через stopService(). */
        fun startIntent(context: Context, workout: Workout, startDelaySec: Int): Intent =
            Intent(context, WorkoutService::class.java)
                .putExtra(EXTRA_RUN_SEC, workout.runSec)
                .putExtra(EXTRA_WALK_SEC, workout.walkSec)
                .putExtra(EXTRA_ROUNDS, workout.rounds)
                .putExtra(EXTRA_START_DELAY_SEC, startDelaySec)
    }
}
