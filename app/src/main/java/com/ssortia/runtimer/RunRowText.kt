package com.ssortia.runtimer

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Поля карточки записи истории. [walkTime] равен null, если шага не было: при одном повторе
 * последний шаг не выполняется, даже если его время задано.
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

/** Поля карточки. Длительность округляется вниз до секунды, время отрезков как на форме. */
fun runRowText(record: RunRecord, formatters: RunFormatters): RunRowText {
    val startedAt = Instant.ofEpochMilli(record.startedAtMs)
    return RunRowText(
        day = formatters.day.format(startedAt),
        weekday = formatters.weekday.format(startedAt),
        runTime = formatTotal(record.runSec * 1000L),
        walkTime = record.effectiveWalkSec.takeIf { it > 0 }?.let { formatTotal(it * 1000L) },
        meta = "×${record.rounds} · ${formatters.time.format(startedAt)}",
        duration = formatElapsed(record.durationMs),
        completed = record.completed,
        date = formatters.date.format(startedAt),
    )
}

/**
 * Группирует записи по месяцу старта в зоне [zone]. Группы идут в порядке первой записи месяца,
 * записи в них в порядке списка. Месяц даёт одну группу, даже если его записи стоят не подряд:
 * ключ группы служит ключом строки в `LazyColumn`, и повтор уронил бы экран.
 */
fun groupRunsByMonth(records: List<RunRecord>, zone: ZoneId): List<RunMonth> =
    records
        .groupBy { YearMonth.from(Instant.ofEpochMilli(it.startedAtMs).atZone(zone)) }
        .map { (yearMonth, monthRecords) -> RunMonth(yearMonth, monthRecords) }

/** Заголовок группы прописными: «НОЯБРЬ 2026». */
fun runMonthTitle(yearMonth: YearMonth, formatters: RunFormatters): String =
    formatters.month.format(yearMonth).uppercase(formatters.month.locale)
