package com.ssortia.runtimer

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Формат даты последней пробежки недели: «20 октября». Создаётся один раз на экран.
 * Шаблон явный, как у [runFormatters]. Не `d MMM`: сокращённый месяц в `ru` пишется с точкой,
 * и в JDK и на телефоне она может различаться. Года нет: программа длится 2–3 месяца.
 */
fun progressDateFormatter(zone: ZoneId, locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM", locale).withZone(zone)

fun progressDateText(startedAtMs: Long, formatter: DateTimeFormatter): String =
    formatter.format(Instant.ofEpochMilli(startedAtMs))

/**
 * Подписи строк недель. [ringPassed] описание круга пройденной недели для TalkBack,
 * [ringCount] шаблон описания кольца «Пробежек %1$d из %2$d».
 */
class ProgressLabels(
    val run: String,
    val walk: String,
    val min: String,
    val sec: String,
    val ringPassed: String,
    val ringCount: String,
)

/**
 * Строка недели на экране прогресса. [date] null, если пробежек не было: экран рисует «—» и
 * скрывает его от TalkBack. [ringDescription] у непройденной недели «Пробежек 2 из 4», у
 * пройденной «Неделя пройдена». [runsSpoken] false у непройденной недели: число «2 раза» видно,
 * но не читается, его уже назвало описание кольца, иначе TalkBack прочитал бы его дважды.
 * У пройденной недели число читается: пробежек может быть больше четырёх.
 */
data class ProgressRowText(
    val title: String,
    val details: String,
    val runs: String,
    val runsSpoken: Boolean,
    val date: String?,
    val ringDescription: String,
)

/**
 * Текст строки недели. [weekTitle] («Неделя 3») и [runsText] («3 раза») уже готовы: склонение
 * берётся из ресурса `<plurals>`.
 */
fun progressRowText(
    week: WeekProgress,
    weekTitle: String,
    runsText: String,
    labels: ProgressLabels,
    formatter: DateTimeFormatter,
): ProgressRowText {
    val preset = presetRowText(
        preset = week.preset,
        weekTitle = weekTitle,
        runLabel = labels.run,
        walkLabel = labels.walk,
        minLabel = labels.min,
        secLabel = labels.sec,
    )
    return ProgressRowText(
        title = preset.title,
        details = preset.details,
        runs = runsText,
        runsSpoken = week.passed,
        date = week.lastCompletedAtMs?.let { progressDateText(it, formatter) },
        ringDescription = if (week.passed) {
            labels.ringPassed
        } else {
            labels.ringCount.format(Locale.ROOT, week.completedRuns, RUNS_TO_PASS_WEEK)
        },
    )
}
