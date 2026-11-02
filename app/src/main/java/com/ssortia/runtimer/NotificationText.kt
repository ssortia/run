package com.ssortia.runtimer

/** Текст уведомления. Подписи приходят параметрами, поэтому функция не зависит от ресурсов Android. */
fun notificationText(
    position: Position,
    rounds: Int,
    runLabel: String,
    walkLabel: String,
    roundLabel: String,
): String {
    val segment = if (position.isRun) runLabel else walkLabel
    return "$segment · ${formatTime(position.remainingMs)} · $roundLabel ${position.round}/$rounds"
}

/** Текст уведомления во время отсчёта до старта. */
fun countdownNotificationText(
    remainingMs: Long,
    countdownLabel: String,
    secondsLabel: String,
): String = "$countdownLabel ${countdownSeconds(remainingMs)} $secondsLabel"
