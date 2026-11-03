package com.ssortia.runtimer

/**
 * Время тренировки за вычетом пауз. Отсчитывается от [startedAt], а не по тикам,
 * поэтому ошибка не накапливается. Время `now` приходит параметром, класс не зависит
 * от `SystemClock`.
 *
 * Команды безопасны при повторе: повторная пауза, продолжение без паузы и пауза
 * после конца тренировки ничего не меняют.
 */
class WorkoutClock(private val totalMs: Long, private val startedAt: Long) {
    private var pausedTotalMs = 0L
    private var pausedAt: Long? = null

    val isPaused: Boolean get() = pausedAt != null

    /** Время тренировки без пауз. На паузе замирает. */
    fun elapsedAt(now: Long): Long = (pausedAt ?: now) - startedAt - pausedTotalMs

    /** Остаток тренировки, не меньше нуля: нужен для таймаута wake lock. */
    fun remainingAt(now: Long): Long = (totalMs - elapsedAt(now)).coerceAtLeast(0L)

    fun pause(now: Long) {
        if (isPaused || elapsedAt(now) >= totalMs) return
        pausedAt = now
    }

    fun resume(now: Long) {
        val since = pausedAt ?: return
        pausedTotalMs += now - since
        pausedAt = null
    }
}
