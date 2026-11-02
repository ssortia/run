package com.ssortia.runtimer

/** Где мы находимся внутри тренировки в данный момент. */
data class Position(
    val index: Int,
    val isRun: Boolean,
    val round: Int,
    val remainingMs: Long,
)

/**
 * Тренировка: rounds повторов пары «бег + ходьба», в последнем повторе только бег.
 * Длительности должны быть больше нуля.
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
fun workoutOrNull(runSec: Int, walkSec: Int, rounds: Int): Workout? =
    if (runSec > 0 && walkSec > 0 && rounds > 0) Workout(runSec, walkSec, rounds) else null

/** Возвращает null для отрицательной задержки: сервис такой запрос отклоняет. */
fun startDelayMsOrNull(delaySec: Int): Long? =
    if (delaySec >= 0) delaySec * 1_000L else null
