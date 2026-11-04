package com.ssortia.runtimer

/** Сколько завершённых пробежек нужно, чтобы неделя считалась пройденной. */
const val RUNS_TO_PASS_WEEK = 4

/** Прогресс одной недели программы. [lastCompletedAtMs] null, если пробежек не было. */
data class WeekProgress(val preset: WorkoutPreset, val completedRuns: Int, val lastCompletedAtMs: Long?) {
    val week: Int get() = preset.week
    val passed: Boolean get() = completedRuns >= RUNS_TO_PASS_WEEK
}

/**
 * Все недели программы по порядку, в том числе без пробежек. Учитываются только завершённые
 * пробежки, результат не зависит от порядка [runs].
 */
fun programProgress(runs: List<RunRecord>): List<WeekProgress> {
    val byWeek = runs.filter { it.completed }.groupBy { presetWeekOf(it) }
    return WORKOUT_PRESETS.map { preset ->
        val weekRuns = byWeek[preset.week].orEmpty()
        WeekProgress(
            preset = preset,
            completedRuns = weekRuns.size,
            lastCompletedAtMs = weekRuns.maxOfOrNull { it.startedAtMs },
        )
    }
}
