package com.ssortia.runtimer

const val DEFAULT_VIBRATION = true

/**
 * Паттерн вибрации для `VibrationEffect.createWaveform(timings, -1)`: чётные элементы это ожидание,
 * нечётные это вибрация. Импульсы длиннее звука сигнала: короткие плохо чувствуются в кармане на бегу.
 * Стартовый сигнал это один длинный импульс, обычный один короткий, финальный два коротких и длинный.
 */
fun vibrationFor(signal: Signal): LongArray = when (signal) {
    Signal.START -> longArrayOf(0, 600)
    Signal.INTERVAL -> longArrayOf(0, 400)
    Signal.FINISH -> longArrayOf(0, 300, 100, 300, 100, 800)
}

fun vibrationDurationMs(signal: Signal): Long = vibrationFor(signal).sum()

/**
 * Сколько сервис ждёт после финального сигнала перед остановкой: `onDestroy` оборвала бы
 * более длинное из звука и вибрации. Что из них длиннее, зависит от звука финала и паттернов.
 */
fun finishWaitMs(): Long =
    maxOf(soundDurationMs(Signal.FINISH), vibrationDurationMs(Signal.FINISH)) + FINISH_TAIL_MS
