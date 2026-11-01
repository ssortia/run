package com.ssortia.runtimer

/** Где мы находимся внутри тренировки в данный момент. */
data class Position(
    val index: Int,
    val isRun: Boolean,
    val round: Int,
    val remainingMs: Long,
)

/** Тренировка: rounds повторов пары «бег + ходьба». Длительности должны быть больше нуля. */
data class Workout(val runSec: Int, val walkSec: Int, val rounds: Int) {
    private val runMs = runSec * 1000L
    private val cycleMs = (runSec + walkSec) * 1000L

    /** Возвращает позицию на момент elapsedMs от старта или null, если тренировка закончилась. */
    fun at(elapsedMs: Long): Position? {
        if (elapsedMs >= cycleMs * rounds) return null
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
}
