package com.ssortia.runtimer

/** Сколько завершённых пробежек нужно, чтобы неделя считалась пройденной. */
const val RUNS_TO_PASS_WEEK = 4

/** Вид узла недели на ленте прогресса. */
enum class WeekMark {
    /** Пройдена и не текущая. */
    PASSED,

    /** Текущая, в том числе пройденная. */
    CURRENT,

    /** Не пройдена и стоит выше текущей. */
    SKIPPED,

    /** Не пройдена и стоит ниже текущей. */
    AHEAD,
}

/** Прогресс одной недели программы. [lastCompletedAtMs] null, если пробежек не было. */
data class WeekProgress(
    val preset: WorkoutPreset,
    val completedRuns: Int,
    val lastCompletedAtMs: Long?,
    /** Неделя последней завершённой пробежки по программе (см. [currentProgramWeek]). */
    val current: Boolean,
    /** Неделя стоит выше текущей в программе. */
    val beforeCurrent: Boolean,
) {
    val week: Int get() = preset.week
    val passed: Boolean get() = completedRuns >= RUNS_TO_PASS_WEEK
    val mark: WeekMark get() = when {
        current -> WeekMark.CURRENT
        passed -> WeekMark.PASSED
        beforeCurrent -> WeekMark.SKIPPED
        else -> WeekMark.AHEAD
    }

    /** Отрезок линии от этой недели к следующей закрашен. */
    val lineToNextFilled: Boolean get() = beforeCurrent || passed
}

/**
 * Текущая неделя программы: неделя последней по [RunRecord.startedAtMs] завершённой пробежки
 * по программе, при равном времени старта большая неделя. Пробежки со своими параметрами и
 * остановленные не учитываются, без подходящих пробежек текущая — первая неделя.
 */
fun currentProgramWeek(runs: List<RunRecord>): Int =
    runs.asSequence()
        .filter { it.completed }
        .mapNotNull { record -> presetWeekOf(record)?.let { record.startedAtMs to it } }
        .maxWithOrNull(compareBy({ it.first }, { it.second }))
        ?.second
        ?: WORKOUT_PRESETS.first().week

/**
 * Все недели программы по порядку, в том числе без пробежек. Учитываются только завершённые
 * пробежки, результат не зависит от порядка [runs].
 *
 * Линия ленты закрашена до текущей недели ([WeekProgress.beforeCurrent]) и после пройденных
 * недель ([WeekProgress.passed]), в том числе ниже текущей. Отрезок последней недели экран не
 * рисует.
 */
fun programProgress(runs: List<RunRecord>): List<WeekProgress> {
    val byWeek = runs.filter { it.completed }.groupBy { presetWeekOf(it) }
    val currentWeek = currentProgramWeek(runs)
    return WORKOUT_PRESETS.map { preset ->
        val weekRuns = byWeek[preset.week].orEmpty()
        WeekProgress(
            preset = preset,
            completedRuns = weekRuns.size,
            lastCompletedAtMs = weekRuns.maxOfOrNull { it.startedAtMs },
            current = preset.week == currentWeek,
            beforeCurrent = preset.week < currentWeek,
        )
    }
}
