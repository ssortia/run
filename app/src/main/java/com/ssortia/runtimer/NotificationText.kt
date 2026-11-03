package com.ssortia.runtimer

/** Текст уведомления. Подписи приходят параметрами, поэтому функция не зависит от ресурсов Android. */
fun notificationText(
    position: Position,
    rounds: Int,
    paused: Boolean,
    runLabel: String,
    walkLabel: String,
    roundLabel: String,
    pausedLabel: String,
): String {
    val segment = if (position.isRun) runLabel else walkLabel
    val text = "$segment · ${formatTime(position.remainingMs)} · $roundLabel ${position.round}/$rounds"
    return if (paused) "$pausedLabel · $text" else text
}

fun countdownNotificationText(
    seconds: Long,
    countdownLabel: String,
    secondsLabel: String,
): String = "$countdownLabel $seconds $secondsLabel"
