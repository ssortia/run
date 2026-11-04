package com.ssortia.runtimer

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Запись истории: одна прошедшая тренировка. `startedAtMs` по системным часам в конце отсчёта,
 * `durationMs` без пауз и без хвоста после финального сигнала.
 */
@Entity(tableName = "runs")
data class RunRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    val runSec: Int,
    val walkSec: Int,
    val rounds: Int,
    val durationMs: Long,
    val completed: Boolean,
)

/**
 * Ходьба, которая выполнялась: при одном повторе последняя ходьба не идёт, а форма разрешает
 * её задать, поэтому она считается нулевой. По этому правилу запись показывает история и
 * относит к неделе программы [presetWeekOf].
 */
val RunRecord.effectiveWalkSec: Int get() = if (rounds == 1) 0 else walkSec

/** Остановленная раньше тренировка в историю не попадает. */
const val MIN_STOPPED_RUN_MS = 30_000L

/**
 * Собирает запись или возвращает null, если тренировка остановлена раньше [MIN_STOPPED_RUN_MS].
 * [elapsedMs] берётся из `WorkoutClock.elapsedAt()`, без пауз. После финала часы идут дальше,
 * поэтому «завершена» выводится из длительности, а сама длительность прижимается к `totalMs`.
 */
fun runRecordOrNull(workout: Workout, startedAtMs: Long, elapsedMs: Long): RunRecord? {
    val completed = elapsedMs >= workout.totalMs
    if (!completed && elapsedMs < MIN_STOPPED_RUN_MS) return null
    return RunRecord(
        startedAtMs = startedAtMs,
        runSec = workout.runSec,
        walkSec = workout.walkSec,
        rounds = workout.rounds,
        durationMs = elapsedMs.coerceAtMost(workout.totalMs),
        completed = completed,
    )
}

/**
 * Переводит момент по часам `elapsedRealtime` ([elapsedAt]) в системное время по паре
 * одновременных показаний [nowWallMs] и [nowElapsedMs].
 */
fun wallTimeAt(nowWallMs: Long, nowElapsedMs: Long, elapsedAt: Long): Long =
    nowWallMs - (nowElapsedMs - elapsedAt)
