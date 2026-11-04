package com.ssortia.runtimer

/** Подписи состава тренировки: «бег», «шаг» (строчные) и «мин». */
class SummaryLabels(
    val run: String,
    val walk: String,
    val min: String,
)

/** Число отрезков: бег и шаг чередуются, последний отрезок всегда бег. */
fun segmentCount(workout: Workout): Int = 2 * workout.rounds - 1

/**
 * Части состава для итога на форме: «11 отрезков», «бег 12 мин», «шаг 10 мин». [segmentsText]
 * уже готов: склонение берётся из ресурса `<plurals>` по [segmentCount]. При одном повторе
 * шага нет, и части про него тоже нет. Видимая строка собирается через `joinToString(" · ")`.
 */
fun workoutSummaryParts(workout: Workout, segmentsText: String, labels: SummaryLabels): List<String> {
    val parts = mutableListOf(segmentsText, "${labels.run} ${durationText(workout.runSec * workout.rounds, labels)}")
    if (workout.rounds > 1) {
        parts += "${labels.walk} ${durationText(workout.walkSec * (workout.rounds - 1), labels)}"
    }
    return parts
}

/** Описание итога для TalkBack одной фразой: «Вся тренировка, 22:00, 11 отрезков, бег 12 мин». */
fun summaryDescription(title: String, time: String, parts: List<String>): String =
    (listOf(title, time) + parts).joinToString(", ")

/** Целые минуты «N мин» без перевода в часы, иначе «м:сс» или «ч:мм:сс». */
private fun durationText(totalSec: Int, labels: SummaryLabels): String =
    if (totalSec % 60 == 0) "${totalSec / 60} ${labels.min}" else formatTotal(totalSec * 1000L)
