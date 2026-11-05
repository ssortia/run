package com.ssortia.runtimer.ui

import com.ssortia.runtimer.WorkoutState
import com.ssortia.runtimer.formatTime
import com.ssortia.runtimer.wholeSeconds
import java.util.Locale

/**
 * Подписи полоски тренировки. Строчные варианты нужны для описания TalkBack, где отрезок и повтор
 * стоят внутри фразы. [minutes] и [seconds] склоняют число из `<plurals>`: «1 минута», «2 минуты».
 */
data class WorkoutStripLabels(
    val run: String,
    val walk: String,
    val runLower: String,
    val walkLower: String,
    val paused: String,
    val countdown: String,
    val round: String,
    val roundLower: String,
    val description: String,
    val pausedDescription: String,
    val countdownDescription: String,
    val minutes: (Int) -> String,
    val seconds: (Int) -> String,
)

/** Текст полоски тренировки над нижней панелью. [progress] равен null на отсчёте: линии нет. */
data class WorkoutStripText(
    val title: String,
    val subtitle: String,
    val time: String,
    val progress: Float?,
    val description: String,
)

/**
 * Текст полоски для [state] или null вне тренировки. [details] — параметры тренировки для отсчёта
 * (`workoutDetailsText`): позиции на отсчёте ещё нет.
 */
fun workoutStripText(state: WorkoutState, details: String, labels: WorkoutStripLabels): WorkoutStripText? =
    when (state) {
        WorkoutState.Idle -> null
        is WorkoutState.Countdown -> {
            val remainingMs = state.seconds * 1000
            WorkoutStripText(
                title = labels.countdown,
                subtitle = details,
                time = formatTime(remainingMs),
                progress = null,
                description = labels.countdownDescription.format(Locale.ROOT, spokenTime(remainingMs, labels)),
            )
        }
        is WorkoutState.Active -> {
            val position = state.position
            val segment = if (position.isRun) labels.run else labels.walk
            val segmentLower = if (position.isRun) labels.runLower else labels.walkLower
            val template = if (state.paused) labels.pausedDescription else labels.description
            WorkoutStripText(
                title = if (state.paused) labels.paused.format(Locale.ROOT, segment) else segment,
                subtitle = labels.round.format(Locale.ROOT, position.round, state.rounds),
                time = formatTime(position.remainingMs),
                progress = state.progress,
                description = template.format(
                    Locale.ROOT,
                    segmentLower,
                    spokenTime(position.remainingMs, labels),
                    labels.roundLower.format(Locale.ROOT, position.round, state.rounds),
                ),
            )
        }
    }

/**
 * Остаток словами для TalkBack: секунды округляются вверх, как на экране, нулевые минуты и секунды
 * не произносятся («45 секунд», «2 минуты»). Нулевой остаток читается как «0 секунд».
 */
private fun spokenTime(remainingMs: Long, labels: WorkoutStripLabels): String {
    val totalSec = wholeSeconds(remainingMs)
    val minutes = (totalSec / 60).toInt()
    val seconds = (totalSec % 60).toInt()
    return buildList {
        if (minutes > 0) add(labels.minutes(minutes))
        if (seconds > 0 || minutes == 0) add(labels.seconds(seconds))
    }.joinToString(" ")
}
