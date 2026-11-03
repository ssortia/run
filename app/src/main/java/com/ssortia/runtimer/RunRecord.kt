package com.ssortia.runtimer

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

/** Строка списка истории: дата старта и подробности тренировки. */
data class RunRowText(val date: String, val details: String)

// Явный шаблон, а не ofLocalizedDateTime: локализованный формат в JDK и на телефоне разный
private const val DATE_PATTERN = "d MMMM yyyy, HH:mm"

/**
 * Текст строки списка. Ходьба без времени не показывается (один повтор без ходьбы).
 * Длительность округляется вниз до секунды, в отличие от остатка в [formatTime].
 */
fun runRowText(
    record: RunRecord,
    zone: ZoneId,
    locale: Locale,
    runLabel: String,
    walkLabel: String,
    roundsLabel: String,
    completedLabel: String,
    stoppedLabel: String,
): RunRowText {
    val date = DateTimeFormatter.ofPattern(DATE_PATTERN, locale)
        .withZone(zone)
        .format(Instant.ofEpochMilli(record.startedAtMs))
    val parts = buildList {
        add("$runLabel ${formatTotal(record.runSec * 1000L)}")
        if (record.walkSec > 0) add("$walkLabel ${formatTotal(record.walkSec * 1000L)}")
        add("$roundsLabel ${record.rounds}")
        // formatTotal округляет вверх, поэтому сначала отбрасываем доли секунды
        add(formatTotal(record.durationMs / 1000 * 1000))
        add(if (record.completed) completedLabel else stoppedLabel)
    }
    return RunRowText(date, parts.joinToString(" · "))
}
