package com.ssortia.runtimer

const val SAMPLE_RATE_HZ = 44_100

// Звук выходит из динамика с задержкой: release() или возврат фокуса без запаса обрежут конец сигнала
const val FINISH_TAIL_MS = 300L

// Система приглушает музыку не сразу после запроса фокуса, а плавно. Поэтому фокус берётся
// заранее, и к моменту сигнала музыка уже тише
const val DUCK_AHEAD_MS = 1_000L

const val DEFAULT_DUCK_MUSIC = true

// Длительности звуков из res/raw: по ним держится фокус и ждёт финал, а сами файлы читаются
// только с Android API. SignalFilesTest сверяет константы с файлами
const val COUNTDOWN_SOUND_MS = 950L
const val START_SOUND_MS = 1220L
const val INTERVAL_SOUND_MS = 370L
const val FINISH_SOUND_MS = 1240L

/**
 * Пора ли приглушать музыку: до ближайшего сигнала осталось не больше [DUCK_AHEAD_MS].
 * На паузе время стоит, и музыка должна звучать в полную громкость. При выключенной [duckMusic]
 * всегда `false`; передавайте её именованным аргументом, рядом стоит булев [paused].
 */
fun shouldDuck(msToSignal: Long, duckMusic: Boolean, paused: Boolean = false): Boolean =
    duckMusic && !paused && msToSignal <= DUCK_AHEAD_MS

/**
 * Пора ли приглушать музыку сразу после продолжения: до конца отрезка, а с ним до сигнала,
 * осталось не больше [DUCK_AHEAD_MS]. Цикл сервиса проверил бы это только на следующем шаге.
 * После конца тренировки сигналов больше нет. При выключенной настройке [duckMusic] всегда `false`.
 */
fun shouldDuckAfterResume(workout: Workout, elapsedMs: Long, duckMusic: Boolean): Boolean =
    workout.at(elapsedMs)?.let { shouldDuck(it.remainingMs, duckMusic = duckMusic) } ?: false

/** Длительность звука сигнала. У каждого сигнала свой звук. */
fun soundDurationMs(signal: Signal): Long = when (signal) {
    Signal.COUNTDOWN -> COUNTDOWN_SOUND_MS
    Signal.START -> START_SOUND_MS
    Signal.INTERVAL -> INTERVAL_SOUND_MS
    Signal.FINISH -> FINISH_SOUND_MS
}

/** Длительность сигнала с запасом [FINISH_TAIL_MS]. Столько `Beeper` держит аудиофокус. */
fun soundWithTailMs(signal: Signal): Long = soundDurationMs(signal) + FINISH_TAIL_MS
