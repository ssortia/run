package com.ssortia.runtimer

const val SAMPLE_RATE_HZ = 44_100

// Звук выходит из динамика с задержкой: release() или возврат фокуса без запаса обрежут конец сигнала
const val FINISH_TAIL_MS = 300L

// Система приглушает музыку не сразу после запроса фокуса, а плавно. Поэтому фокус берётся
// заранее, и к моменту сигнала музыка уже тише
const val DUCK_AHEAD_MS = 1_000L

// Длительности звуков из res/raw: по ним держится фокус и ждёт финал, а сами файлы читаются
// только с Android API. SignalFilesTest сверяет константы с файлами
const val INTERVAL_SOUND_MS = 370L
const val FINISH_SOUND_MS = 1240L

/**
 * Пора ли приглушать музыку: до ближайшего сигнала осталось не больше [DUCK_AHEAD_MS].
 * На паузе время стоит, и музыка должна звучать в полную громкость.
 */
fun shouldDuck(msToSignal: Long, paused: Boolean = false): Boolean =
    !paused && msToSignal <= DUCK_AHEAD_MS

/** Длительность звука сигнала. Старт звучит как смена отрезка. */
fun soundDurationMs(signal: Signal): Long = when (signal) {
    Signal.START, Signal.INTERVAL -> INTERVAL_SOUND_MS
    Signal.FINISH -> FINISH_SOUND_MS
}

/** Длительность сигнала с запасом [FINISH_TAIL_MS]. Столько `Beeper` держит аудиофокус. */
fun soundWithTailMs(signal: Signal): Long = soundDurationMs(signal) + FINISH_TAIL_MS
