package com.ssortia.runtimer

/** Подписи состава тренировки: «бег», «шаг» (строчные) и «мин». */
class SummaryLabels(
    val run: String,
    val walk: String,
    val min: String,
)

/** Число отрезков: бег и шаг чередуются, последний отрезок всегда бег. */
fun segmentCount(workout: Workout): Int = 2 * workout.rounds - 1

/** Итог тренировки на форме: общее время и части состава («11 отрезков», «бег 12 мин»). */
data class WorkoutSummaryText(val time: String, val parts: List<String>) {
    /** Видимая строка состава: «11 отрезков · бег 12 мин · шаг 10 мин». */
    val text: String get() = parts.joinToString(" · ")

    /** Описание для TalkBack одной фразой: «Вся тренировка, 22:00, 11 отрезков, бег 12 мин». */
    fun description(title: String): String = (listOf(title, time) + parts).joinToString(", ")
}

/**
 * Итог для формы или null при неверных настройках. [error] — результат `settings.validate()`:
 * его считает вызывающий один раз на всю форму, и при ошибке `toWorkout()` не вызывается.
 * [segmentsText] склоняет «N отрезков» по [segmentCount], строка берётся из ресурса `<plurals>`.
 */
fun workoutSummaryText(
    settings: WorkoutSettings,
    error: SettingsError?,
    labels: SummaryLabels,
    segmentsText: (Int) -> String,
): WorkoutSummaryText? {
    if (error != null) return null
    val workout = settings.toWorkout()
    return WorkoutSummaryText(
        time = formatTotal(workout.totalMs),
        parts = workoutSummaryParts(workout, segmentsText(segmentCount(workout)), labels),
    )
}

/**
 * Части состава: «11 отрезков», «бег 12 мин», «шаг 10 мин». [segmentsText] уже готов. При одном
 * повторе шага нет, и части про него тоже нет.
 */
fun workoutSummaryParts(workout: Workout, segmentsText: String, labels: SummaryLabels): List<String> {
    val parts = mutableListOf(segmentsText, "${labels.run} ${durationText(workout.runSec * workout.rounds, labels)}")
    if (workout.rounds > 1) {
        parts += "${labels.walk} ${durationText(workout.walkSec * (workout.rounds - 1), labels)}"
    }
    return parts
}

/** Целые минуты «N мин» без перевода в часы, иначе «м:сс» или «ч:мм:сс». */
private fun durationText(totalSec: Int, labels: SummaryLabels): String =
    if (totalSec % 60 == 0) "${totalSec / 60} ${labels.min}" else formatTotal(totalSec * 1000L)
