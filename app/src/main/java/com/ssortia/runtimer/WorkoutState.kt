package com.ssortia.runtimer

import java.util.Locale

/** Состояние тренировки, которое сервис публикует для экрана. */
sealed interface WorkoutState {
    data object Idle : WorkoutState
    data class Active(val position: Position, val rounds: Int, val progress: Float) : WorkoutState
}

enum class Signal { INTERVAL, FINISH }

/**
 * Решает, какой сигнал подать на текущем шаге цикла.
 * [previousIndex] равен null на первом шаге: начало первого отрезка сигнала не даёт.
 * [position] равна null, если тренировка закончилась.
 */
fun signalFor(previousIndex: Int?, position: Position?): Signal? = when {
    position == null -> Signal.FINISH
    previousIndex == null || previousIndex == position.index -> null
    else -> Signal.INTERVAL
}

/** Форматирует остаток как «м:сс». Секунды округляются вверх, поэтому внутри отрезка нет `0:00`. */
fun formatTime(remainingMs: Long): String {
    val totalSec = (remainingMs.coerceAtLeast(0) + 999) / 1000
    return "%d:%02d".format(Locale.ROOT, totalSec / 60, totalSec % 60)
}
