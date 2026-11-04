package com.ssortia.runtimer

const val WAKE_LOCK_MARGIN_MS = 60_000L

/** Где мы находимся внутри тренировки в данный момент. */
data class Position(
    val index: Int,
    val isRun: Boolean,
    val round: Int,
    val remainingMs: Long,
)

/**
 * Тренировка: rounds повторов пары «бег + шаг», в последнем повторе только бег.
 * Время бега и число повторов больше нуля, время шага тоже, кроме тренировки из одного
 * повтора: там шага нет, и он может быть нулевым.
 */
data class Workout(val runSec: Int, val walkSec: Int, val rounds: Int) {
    private val runMs = runSec * 1000L
    private val cycleMs = (runSec + walkSec) * 1000L

    val totalMs = cycleMs * (rounds - 1) + runMs

    /** Возвращает позицию на момент elapsedMs от старта или null, если тренировка закончилась. */
    fun at(elapsedMs: Long): Position? {
        if (elapsedMs >= totalMs) return null
        val round = (elapsedMs / cycleMs).toInt()
        val inCycle = elapsedMs % cycleMs
        val isRun = inCycle < runMs
        return Position(
            index = round * 2 + if (isRun) 0 else 1,
            isRun = isRun,
            round = round + 1,
            remainingMs = (if (isRun) runMs else cycleMs) - inCycle,
        )
    }

    /** Доля пройденного времени от 0 до 1. Значения вне тренировки прижимаются к границам. */
    fun progressAt(elapsedMs: Long): Float =
        (elapsedMs.toDouble() / totalMs).toFloat().coerceIn(0f, 1f)
}

/** Возвращает null, если параметры не дают корректную тренировку: сервис такой запрос отклоняет. */
fun workoutOrNull(runSec: Int, walkSec: Int, rounds: Int): Workout? = when {
    runSec <= 0 || rounds <= 0 || walkSec < 0 -> null
    // То же правило, что в WorkoutSettings.validate(): без шага можно только при одном повторе
    walkSec == 0 && rounds > 1 -> null
    else -> Workout(runSec, walkSec, rounds)
}

/** Таймаут wake lock страхует от разряда батареи при ошибке в логике. */
fun wakeLockTimeoutMs(totalMs: Long, delayMs: Long): Long = totalMs + delayMs + WAKE_LOCK_MARGIN_MS
