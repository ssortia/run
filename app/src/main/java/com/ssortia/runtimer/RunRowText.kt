package com.ssortia.runtimer

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Поля карточки записи истории. [walkTime] равен null, если ходьбы нет (один повтор без ходьбы).
 * [date] полная дата старта для диалога удаления и описания корзины.
 */
data class RunRowText(
    val day: String,
    val weekday: String,
    val runTime: String,
    val walkTime: String?,
    val meta: String,
    val duration: String,
    val completed: Boolean,
    val date: String,
)

/** Группа истории: записи одного месяца старта в порядке исходного списка. */
data class RunMonth(val yearMonth: YearMonth, val records: List<RunRecord>)

/**
 * Форматы экрана истории. Создаются один раз на экран функцией [runFormatters], а не на каждую
 * карточку. Шаблоны явные, а не ofLocalized…: локализованный формат в JDK и на телефоне разный.
 */
class RunFormatters(
    val date: DateTimeFormatter,
    val day: DateTimeFormatter,
    val weekday: DateTimeFormatter,
    val time: DateTimeFormatter,
    val month: DateTimeFormatter,
)

fun runFormatters(zone: ZoneId, locale: Locale): RunFormatters {
    fun pattern(value: String) = DateTimeFormatter.ofPattern(value, locale).withZone(zone)
    return RunFormatters(
        date = pattern("d MMMM yyyy, HH:mm"),
        day = pattern("d"),
        weekday = pattern("EEE"),
        time = pattern("HH:mm"),
        // LLLL — самостоятельная форма месяца: «ноябрь», а не «ноября»
        month = DateTimeFormatter.ofPattern("LLLL yyyy", locale),
    )
}

/** Полная дата старта: для диалога удаления и описания корзины. */
fun runDateText(startedAtMs: Long, formatters: RunFormatters): String =
    formatters.date.format(Instant.ofEpochMilli(startedAtMs))

/** Поля карточки. Длительность округляется вниз до секунды, время отрезков как на форме. */
fun runRowText(record: RunRecord, formatters: RunFormatters): RunRowText {
    val startedAt = Instant.ofEpochMilli(record.startedAtMs)
    return RunRowText(
        day = formatters.day.format(startedAt),
        weekday = formatters.weekday.format(startedAt),
        runTime = formatTotal(record.runSec * 1000L),
        walkTime = if (record.walkSec > 0) formatTotal(record.walkSec * 1000L) else null,
        meta = "×${record.rounds} · ${formatters.time.format(startedAt)}",
        duration = formatElapsed(record.durationMs),
        completed = record.completed,
        date = formatters.date.format(startedAt),
    )
}

/**
 * Группирует уже отсортированные записи по месяцу старта в зоне [zone]. Порядок групп и записей
 * в них сохраняется: соседние записи одного месяца попадают в одну группу.
 */
fun groupRunsByMonth(records: List<RunRecord>, zone: ZoneId): List<RunMonth> {
    val groups = mutableListOf<Pair<YearMonth, MutableList<RunRecord>>>()
    for (record in records) {
        val yearMonth = YearMonth.from(Instant.ofEpochMilli(record.startedAtMs).atZone(zone))
        val last = groups.lastOrNull()
        if (last != null && last.first == yearMonth) last.second += record
        else groups += yearMonth to mutableListOf(record)
    }
    return groups.map { (yearMonth, monthRecords) -> RunMonth(yearMonth, monthRecords) }
}

/** Заголовок группы: «ноябрь 2026». Прописными его делает экран. */
fun runMonthTitle(yearMonth: YearMonth, formatters: RunFormatters): String =
    formatters.month.format(yearMonth)
