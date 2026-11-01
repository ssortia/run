package com.ssortia.runtimer

import java.util.Locale

private const val SECONDS_IN_HOUR = 3_600L

/** Состояние тренировки, которое сервис публикует для экрана. */
sealed interface WorkoutState {
    data object Idle : WorkoutState
    data class Active(
        val position: Position,
        val rounds: Int,
        val progress: Float,
        val soundFailed: Boolean = false,
    ) : WorkoutState
}

enum class Signal { INTERVAL, FINISH }

/** Результат одного шага цикла тренировки. */
data class WorkoutTick(
    val state: WorkoutState.Active,
    val signal: Signal?,
    val finished: Boolean,
)

/**
 * Считает шаг цикла на момент [elapsedMs]. На следующий шаг [previousIndex] берётся из
 * `state.position.index`. После конца показывается последний отрезок с нулевым остатком,
 * даже если шаг запоздал и перепрыгнул его.
 */
fun tickAt(
    workout: Workout,
    elapsedMs: Long,
    previousIndex: Int?,
    soundFailed: Boolean = false,
): WorkoutTick {
    val position = workout.at(elapsedMs)
    val shown = position ?: checkNotNull(workout.at(workout.totalMs - 1)).copy(remainingMs = 0)
    return WorkoutTick(
        state = WorkoutState.Active(shown, workout.rounds, workout.progressAt(elapsedMs), soundFailed),
        signal = signalFor(previousIndex, position),
        finished = position == null,
    )
}

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
    val totalSec = wholeSeconds(remainingMs)
    return "%d:%02d".format(Locale.ROOT, totalSec / 60, totalSec % 60)
}

/** Форматирует общую длительность: «м:сс», а начиная с часа «ч:мм:сс». */
fun formatTotal(totalMs: Long): String {
    val totalSec = wholeSeconds(totalMs)
    if (totalSec < SECONDS_IN_HOUR) return formatTime(totalMs)
    val minutes = totalSec % SECONDS_IN_HOUR / 60
    return "%d:%02d:%02d".format(Locale.ROOT, totalSec / SECONDS_IN_HOUR, minutes, totalSec % 60)
}

private fun wholeSeconds(ms: Long): Long = (ms.coerceAtLeast(0) + 999) / 1000
