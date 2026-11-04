package com.ssortia.runtimer

/** Строка недели в шторке программы: заголовок, параметры и общее время. */
data class PresetRowText(val title: String, val details: String, val total: String)

/**
 * Текст строки недели. [weekTitle] уже готов («Неделя 3»). Шаг без времени не показывается
 * (неделя 10). Общее время равно длительности тренировки: последний шаг в него не входит.
 */
fun presetRowText(
    preset: WorkoutPreset,
    weekTitle: String,
    runLabel: String,
    walkLabel: String,
    minLabel: String,
    secLabel: String,
): PresetRowText {
    val workout = preset.settings.toWorkout()
    val details = buildList {
        add("$runLabel ${formatTotal(workout.runSec * 1000L)}")
        if (workout.walkSec > 0) add("$walkLabel ${formatTotal(workout.walkSec * 1000L)}")
        add("×${workout.rounds}")
    }.joinToString(" · ")
    return PresetRowText(weekTitle, details, formatPresetTotal(workout.totalMs, minLabel, secLabel))
}

/**
 * Общее время недели в шторке программы словами: «23 мин», «19 мин 30 с». Часы не выделяются,
 * час пишется как «60 мин». Остальные форматы времени («м:сс») лежат в `WorkoutState.kt`.
 */
fun formatPresetTotal(totalMs: Long, minLabel: String, secLabel: String): String {
    val totalSec = totalMs.coerceAtLeast(0) / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return when {
        seconds == 0L -> "$minutes $minLabel"
        minutes == 0L -> "$seconds $secLabel"
        else -> "$minutes $minLabel $seconds $secLabel"
    }
}
