package com.ssortia.runtimer

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Строка списка истории: дата старта и подробности тренировки. */
data class RunRowText(val date: String, val details: String)

// Явный шаблон, а не ofLocalizedDateTime: локализованный формат в JDK и на телефоне разный
private const val DATE_PATTERN = "d MMMM yyyy, HH:mm"

/** Формат даты старта. Создаётся один раз на экран, а не на каждую строку. */
fun runDateFormatter(zone: ZoneId, locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DATE_PATTERN, locale).withZone(zone)

fun runDateText(startedAtMs: Long, formatter: DateTimeFormatter): String =
    formatter.format(Instant.ofEpochMilli(startedAtMs))

/** Текст строки списка. Ходьба без времени не показывается (один повтор без ходьбы). */
fun runRowText(
    record: RunRecord,
    dateFormatter: DateTimeFormatter,
    runLabel: String,
    walkLabel: String,
    roundsLabel: String,
    completedLabel: String,
    stoppedLabel: String,
): RunRowText {
    val parts = buildList {
        add("$runLabel ${formatTotal(record.runSec * 1000L)}")
        if (record.walkSec > 0) add("$walkLabel ${formatTotal(record.walkSec * 1000L)}")
        add("$roundsLabel ${record.rounds}")
        add(formatElapsed(record.durationMs))
        add(if (record.completed) completedLabel else stoppedLabel)
    }
    return RunRowText(runDateText(record.startedAtMs, dateFormatter), parts.joinToString(" · "))
}
