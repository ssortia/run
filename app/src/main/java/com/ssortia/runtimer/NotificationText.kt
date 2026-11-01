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
