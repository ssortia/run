package com.ssortia.runtimer

import java.util.Locale

/**
 * Подписи полоски тренировки. Строчные варианты нужны для описания TalkBack, где отрезок и повтор
 * стоят внутри фразы. [minutes] и [seconds] склоняют число из `<plurals>` в именительном падеже
 * («осталось 1 минута»), [minutesAccusative] и [secondsAccusative] в винительном для отсчёта
 * («старт через 1 минуту»).
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
    val minutesAccusative: (Int) -> String,
    val secondsAccusative: (Int) -> String,
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
 * Текст полоски для [state] или null вне тренировки. [workout] — запущенная тренировка: на отсчёте
 * позиции ещё нет, и вместо повтора показываются её параметры (`workoutDetailsText`).
 */
fun workoutStripText(state: WorkoutState, workout: Workout, labels: WorkoutStripLabels): WorkoutStripText? =
    when (state) {
        WorkoutState.Idle -> null
        is WorkoutState.Countdown -> {
            val remainingMs = state.seconds * 1000L
            WorkoutStripText(
                title = labels.countdown,
                subtitle = workoutDetailsText(workout, labels.run, labels.walk),
                time = formatTime(remainingMs),
                progress = null,
                description = labels.countdownDescription.format(
                    Locale.ROOT,
                    spokenTime(remainingMs, labels.minutesAccusative, labels.secondsAccusative),
                ),
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
                    spokenTime(position.remainingMs, labels.minutes, labels.seconds),
                    labels.roundLower.format(Locale.ROOT, position.round, state.rounds),
                ),
            )
        }
    }

/**
 * Остаток словами для TalkBack: секунды округляются вверх, как на экране, нулевые минуты и секунды
 * не произносятся («45 секунд», «2 минуты»). Нулевой остаток читается как «0 секунд».
 */
private fun spokenTime(remainingMs: Long, minutes: (Int) -> String, seconds: (Int) -> String): String {
    val totalSec = wholeSeconds(remainingMs)
    val wholeMinutes = (totalSec / 60).toInt()
    val restSeconds = (totalSec % 60).toInt()
    return buildList {
        if (wholeMinutes > 0) add(minutes(wholeMinutes))
        if (restSeconds > 0 || wholeMinutes == 0) add(seconds(restSeconds))
    }.joinToString(" ")
}
