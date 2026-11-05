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
 * Подписи строк недель. [statusCount] шаблон строки состояния «Пробежек %1$d из %2$d»,
 * [statusPassed] шаблон «Неделя пройдена · %s» вместе с разделителем, [none] «—» вместо числа
 * пробежек у недели без пробежек. Остальные подписи —
 * части описания строки для TalkBack, со строчной буквы: [now] «сейчас», [passed] «пройдена»,
 * [last] «последняя %s», [noRuns] «пробежек нет», [count] «пробежек %1$d из %2$d».
 */
class ProgressLabels(
    val run: String,
    val walk: String,
    val min: String,
    val sec: String,
    val statusCount: String,
    val statusPassed: String,
    val none: String,
    val now: String,
    val passed: String,
    val last: String,
    val noRuns: String,
    val count: String,
)

/**
 * Строка недели на экране прогресса. [runs] число пробежек («3 раза») или [ProgressLabels.none]
 * «—», если их не было, [date] тогда null.
 * [status] строка состояния карточки «Сейчас», у остальных недель null. [description] вся
 * строка одной фразой для TalkBack: видимые элементы карточки от него скрыты, иначе число
 * и дата читались бы дважды.
 */
data class ProgressRowText(
    val title: String,
    val details: String,
    val runs: String,
    val date: String?,
    val status: String?,
    val description: String,
)

/**
 * Текст строки недели. [weekTitle] («Неделя 3») и [runsText] («3 раза») уже готовы: склонение
 * берётся из ресурса `<plurals>`. У текущей недели строка состояния до порога «Пробежек 3 из 4»,
 * с порога «Неделя пройдена · 5 раз»: деления не растут дальше четырёх, а число пробежек может.
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
    val date = week.lastCompletedAtMs?.let { progressDateText(it, formatter) }
    val status = when {
        !week.current -> null
        week.passed -> labels.statusPassed.format(Locale.ROOT, runsText)
        else -> labels.statusCount.format(Locale.ROOT, week.completedRuns, RUNS_TO_PASS_WEEK)
    }
    val description = buildList {
        add(preset.title)
        add(preset.details)
        if (week.current) add(labels.now)
        when {
            week.passed -> {
                add(labels.passed)
                add(runsText)
            }
            week.completedRuns > 0 -> add(labels.count.format(Locale.ROOT, week.completedRuns, RUNS_TO_PASS_WEEK))
            else -> add(labels.noRuns)
        }
        if (date != null) add(labels.last.format(Locale.ROOT, date))
    }.joinToString(", ")
    return ProgressRowText(
        title = preset.title,
        details = preset.details,
        runs = if (week.completedRuns > 0) runsText else labels.none,
        date = date,
        status = status,
        description = description,
    )
}
