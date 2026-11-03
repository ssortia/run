package com.ssortia.runtimer

/** Неделя программы и её настройки. Подставляется в форму целиком, отдельно не хранится. */
data class WorkoutPreset(val week: Int, val settings: WorkoutSettings)

/** Программа на 11 недель. Ходьба 90 сек записана как 1 мин 30 сек, как в полях формы. */
val WORKOUT_PRESETS: List<WorkoutPreset> = listOf(
    WorkoutPreset(1, WorkoutSettings(runMin = 1, runSec = 0, walkMin = 2, walkSec = 0, rounds = 7)),
    WorkoutPreset(2, WorkoutSettings(runMin = 2, runSec = 0, walkMin = 2, walkSec = 0, rounds = 7)),
    WorkoutPreset(3, WorkoutSettings(runMin = 3, runSec = 0, walkMin = 2, walkSec = 0, rounds = 5)),
    WorkoutPreset(4, WorkoutSettings(runMin = 4, runSec = 0, walkMin = 2, walkSec = 0, rounds = 4)),
    WorkoutPreset(5, WorkoutSettings(runMin = 6, runSec = 0, walkMin = 2, walkSec = 0, rounds = 3)),
    WorkoutPreset(6, WorkoutSettings(runMin = 8, runSec = 0, walkMin = 1, walkSec = 30, rounds = 3)),
    WorkoutPreset(7, WorkoutSettings(runMin = 9, runSec = 0, walkMin = 1, walkSec = 30, rounds = 2)),
    WorkoutPreset(8, WorkoutSettings(runMin = 11, runSec = 0, walkMin = 1, walkSec = 30, rounds = 2)),
    WorkoutPreset(9, WorkoutSettings(runMin = 13, runSec = 0, walkMin = 1, walkSec = 0, rounds = 2)),
    WorkoutPreset(10, WorkoutSettings(runMin = 15, runSec = 0, walkMin = 1, walkSec = 0, rounds = 2)),
    WorkoutPreset(11, WorkoutSettings(runMin = 30, runSec = 0, walkMin = 0, walkSec = 0, rounds = 1)),
)

/** Неделя, настройки которой точно совпадают с [settings], или null. */
fun presetWeekOf(settings: WorkoutSettings): Int? =
    WORKOUT_PRESETS.firstOrNull { it.settings == settings }?.week
