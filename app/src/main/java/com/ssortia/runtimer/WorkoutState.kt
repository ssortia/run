package com.ssortia.runtimer

import java.util.Locale

private const val SECONDS_IN_HOUR = 3_600L

/** Шаг цикла сервиса. */
const val TICK_MS = 250L

/**
 * Состояние тренировки, которое сервис публикует для экрана. `soundFailed`: сигналы не удалось
 * подготовить. `soundMuted`: громкость медиа на нуле, она проверяется на каждом шаге и может
 * меняться на бегу. `paused`: тренировка на паузе, время замерло.
 */
sealed interface WorkoutState {
    data object Idle : WorkoutState

    /** Отсчёт до старта. Позиции и прогресса у него нет: тренировка ещё не началась. */
    data class Countdown(
        val seconds: Long,
        val soundFailed: Boolean = false,
        val soundMuted: Boolean = false,
    ) : WorkoutState

    data class Active(
        val position: Position,
        val rounds: Int,
        val progress: Float,
        val soundFailed: Boolean = false,
        val soundMuted: Boolean = false,
        val paused: Boolean = false,
    ) : WorkoutState
}

enum class Signal { START, INTERVAL, FINISH }

/** Результат одного шага цикла тренировки. */
data class WorkoutTick(
    val state: WorkoutState.Active,
    val signal: Signal?,
    val finished: Boolean,
)

/** Результат одного шага отсчёта: что показать и сколько ждать до следующего шага. */
data class CountdownTick(val state: WorkoutState.Countdown, val waitMs: Long)

/**
 * Считает шаг отсчёта на момент [now] или возвращает null, если [startedAt] уже наступил.
 * Ожидание не длиннее остатка, чтобы `START` не опоздал на шаг цикла.
 */
fun countdownTickAt(
    startedAt: Long,
    now: Long,
    soundFailed: Boolean = false,
    soundMuted: Boolean = false,
): CountdownTick? {
    val remainingMs = startedAt - now
    if (remainingMs <= 0) return null
    return CountdownTick(
        state = WorkoutState.Countdown(wholeSeconds(remainingMs), soundFailed, soundMuted),
        waitMs = minOf(TICK_MS, remainingMs),
    )
}

/**
 * Считает шаг цикла на момент [elapsedMs]. На следующий шаг [previousIndex] берётся из
 * `state.position.index`. После конца показывается последний отрезок с нулевым остатком,
 * даже если шаг запоздал и перепрыгнул его. На паузе [elapsedMs] замирает, поэтому сигнал
 * будет, только если граница отрезка пройдена до паузы.
 */
fun tickAt(
    workout: Workout,
    elapsedMs: Long,
    previousIndex: Int?,
    soundFailed: Boolean = false,
    soundMuted: Boolean = false,
    paused: Boolean = false,
): WorkoutTick {
    val position = workout.at(elapsedMs)
    val shown = position ?: checkNotNull(workout.at(workout.totalMs - 1)).copy(remainingMs = 0)
    return WorkoutTick(
        state = WorkoutState.Active(
            position = shown,
            rounds = workout.rounds,
            progress = workout.progressAt(elapsedMs),
            soundFailed = soundFailed,
            soundMuted = soundMuted,
            paused = paused,
        ),
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

/** Секунды округляются вверх: отсчёт идёт 5, 4, 3, 2, 1 и не показывает 0. */
internal fun wholeSeconds(ms: Long): Long = (ms.coerceAtLeast(0) + 999) / 1000
