package com.ssortia.runtimer

/** Неделя программы и её настройки. Подставляется в форму целиком, отдельно не хранится. */
data class WorkoutPreset(val week: Int, val settings: WorkoutSettings)

/**
 * Программа на 10 недель. Бег за тренировку растёт на 2–3 минуты в неделю (на второй неделе на 5),
 * а перед последней неделей те же 30 минут бега идут двумя отрезками. Шаг держится 2 минуты
 * до недели 8 и сокращается один раз, на неделе 9, где отрезок бега растёт всего на минуту.
 * Отрезки кратны 30 сек, общее время каждой недели — целые минуты. Бег 270 сек записан
 * как 4 мин 30 сек, как в форме.
 */
val WORKOUT_PRESETS: List<WorkoutPreset> = listOf(
    WorkoutPreset(1, WorkoutSettings(runMin = 1, runSec = 0, walkMin = 2, walkSec = 0, rounds = 7)),
    WorkoutPreset(2, WorkoutSettings(runMin = 2, runSec = 0, walkMin = 2, walkSec = 0, rounds = 6)),
    WorkoutPreset(3, WorkoutSettings(runMin = 3, runSec = 0, walkMin = 2, walkSec = 0, rounds = 5)),
    WorkoutPreset(4, WorkoutSettings(runMin = 4, runSec = 30, walkMin = 2, walkSec = 0, rounds = 4)),
    WorkoutPreset(5, WorkoutSettings(runMin = 7, runSec = 0, walkMin = 2, walkSec = 0, rounds = 3)),
    WorkoutPreset(6, WorkoutSettings(runMin = 8, runSec = 0, walkMin = 2, walkSec = 0, rounds = 3)),
    WorkoutPreset(7, WorkoutSettings(runMin = 13, runSec = 0, walkMin = 2, walkSec = 0, rounds = 2)),
    WorkoutPreset(8, WorkoutSettings(runMin = 14, runSec = 0, walkMin = 2, walkSec = 0, rounds = 2)),
    WorkoutPreset(9, WorkoutSettings(runMin = 15, runSec = 0, walkMin = 1, walkSec = 0, rounds = 2)),
    WorkoutPreset(10, WorkoutSettings(runMin = 30, runSec = 0, walkMin = 0, walkSec = 0, rounds = 1)),
)

/** Неделя, настройки которой точно совпадают с [settings], или null. */
fun presetWeekOf(settings: WorkoutSettings): Int? =
    WORKOUT_PRESETS.firstOrNull { it.settings == settings }?.week

/**
 * Недели прежней программы, у которых менялись параметры: до ноября 2026 шаг недель 5–6 был
 * 1:30, недель 7–8 одна минута. По ним засчитываются пробежки из истории, а форма и шторка
 * их не знают. Тройки не должны совпадать с [WORKOUT_PRESETS].
 */
internal val PREVIOUS_PRESET_WORKOUTS: List<Pair<Int, Workout>> = listOf(
    5 to Workout(runSec = 420, walkSec = 90, rounds = 3),
    6 to Workout(runSec = 480, walkSec = 90, rounds = 3),
    7 to Workout(runSec = 780, walkSec = 60, rounds = 2),
    8 to Workout(runSec = 840, walkSec = 60, rounds = 2),
)

/**
 * Неделя с теми же бегом, шагом и повторами, что у [record], или null. Шаг берётся
 * выполненный ([effectiveWalkSec]): при одном повторе он нулевой. Пробежки по прежним
 * параметрам недель ([PREVIOUS_PRESET_WORKOUTS]) засчитываются своей неделе.
 */
fun presetWeekOf(record: RunRecord): Int? {
    val workout = Workout(record.runSec, record.effectiveWalkSec, record.rounds)
    return WORKOUT_PRESETS.firstOrNull { it.settings.toWorkout() == workout }?.week
        ?: PREVIOUS_PRESET_WORKOUTS.firstOrNull { it.second == workout }?.first
}
