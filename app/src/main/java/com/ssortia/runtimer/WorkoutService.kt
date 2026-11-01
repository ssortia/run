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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Ведёт тренировку при выключенном экране. Цикл и все методы сервиса работают в главном потоке,
 * поэтому после [cleanup] цикл уже не может подать сигнал или обновить уведомление.
 */
class WorkoutService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var beeper: Beeper? = null

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false) }
    }
    private val openAppIntent by lazy {
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
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
            buildNotification(currentText()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (job != null) return START_NOT_STICKY

        val workout = intent?.toWorkout()
        if (workout == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startWorkout(workout)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cleanup()
        scope.cancel()
        super.onDestroy()
    }

    private fun startWorkout(workout: Workout) {
        // Таймаут страхует от разряда батареи при ошибке в логике
        wakeLock.acquire(workout.totalMs + WAKE_LOCK_MARGIN_MS)
        val sounds = Beeper().also { beeper = it }
        val startedAt = SystemClock.elapsedRealtime()
        job = scope.launch { runLoop(workout, startedAt, sounds) }
    }

    private suspend fun runLoop(workout: Workout, startedAt: Long, sounds: Beeper) {
        var previousIndex: Int? = null
        // Запасное значение нужно, если первый шаг цикла запоздал дольше всей тренировки
        var lastPosition = checkNotNull(workout.at(0))
        var shownText: String? = null

        while (true) {
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            val position = workout.at(elapsedMs)
            val state = if (position != null) {
                WorkoutState.Active(position, workout.rounds, workout.progressAt(elapsedMs))
            } else {
                WorkoutState.Active(lastPosition.copy(remainingMs = 0), workout.rounds, 1f)
            }
            _state.value = state

            // Сравнивается весь текст: при отрезках в 1 с секунды совпадают, а отрезок уже другой
            val text = textFor(state)
            if (text != shownText) {
                notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
                shownText = text
            }

            val soundMs = signalFor(previousIndex, position)?.let(sounds::play) ?: 0
            if (position == null) {
                delay(soundMs)
                break
            }
            previousIndex = position.index
            lastPosition = position
            delay(TICK_MS)
        }

        cleanup()
        stopSelf()
    }

    /** Единое завершение для «Стоп», финала и onDestroy. Вызывать можно повторно. */
    private fun cleanup() {
        job?.cancel()
        job = null
        beeper?.release()
        beeper = null
        if (wakeLock.isHeld) wakeLock.release()
        _state.value = WorkoutState.Idle
    }

    private fun currentText(): String =
        (state.value as? WorkoutState.Active)?.let(::textFor) ?: getString(R.string.app_name)

    private fun textFor(state: WorkoutState.Active): String = notificationText(
        position = state.position,
        rounds = state.rounds,
        runLabel = getString(R.string.segment_run),
        walkLabel = getString(R.string.segment_walk),
        roundLabel = getString(R.string.round_label),
    )

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /** Возвращает null, если параметры не дают корректную тренировку. */
    private fun Intent.toWorkout(): Workout? {
        val runSec = getIntExtra(EXTRA_RUN_SEC, 0)
        val walkSec = getIntExtra(EXTRA_WALK_SEC, 0)
        val rounds = getIntExtra(EXTRA_ROUNDS, 0)
        return if (runSec > 0 && walkSec > 0 && rounds > 0) Workout(runSec, walkSec, rounds) else null
    }

    companion object {
        private const val CHANNEL_ID = "workout"
        private const val NOTIFICATION_ID = 1
        private const val TICK_MS = 250L
        private const val WAKE_LOCK_TAG = "RunTimer:workout"
        private const val WAKE_LOCK_MARGIN_MS = 60_000L
        private const val EXTRA_RUN_SEC = "runSec"
        private const val EXTRA_WALK_SEC = "walkSec"
        private const val EXTRA_ROUNDS = "rounds"

        private val _state = MutableStateFlow<WorkoutState>(WorkoutState.Idle)
        val state: StateFlow<WorkoutState> = _state.asStateFlow()

        /** Интент для startForegroundService(). Останавливать сервис нужно через stopService(). */
        fun startIntent(context: Context, workout: Workout): Intent =
            Intent(context, WorkoutService::class.java)
                .putExtra(EXTRA_RUN_SEC, workout.runSec)
                .putExtra(EXTRA_WALK_SEC, workout.walkSec)
                .putExtra(EXTRA_ROUNDS, workout.rounds)
    }
}
