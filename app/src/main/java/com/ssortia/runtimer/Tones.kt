package com.ssortia.runtimer

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

const val SAMPLE_RATE_HZ = 44_100

// Запас по амплитуде: полный размах на динамике телефона даёт искажения
internal const val PEAK_AMPLITUDE = 26_000
internal const val FADE_SAMPLES = SAMPLE_RATE_HZ * 5 / 1000

// Звук выходит из динамика с задержкой: release() или возврат фокуса без запаса обрежут конец сигнала
const val FINISH_TAIL_MS = 300L

/** Отрезок сигнала. При нулевой частоте это пауза. */
data class Tone(val frequencyHz: Int, val durationMs: Int)

/**
 * Состав сигнала: стартовый звучит одним длинным высоким тоном, обычный двумя короткими,
 * финальный тремя восходящими.
 */
fun tonesFor(signal: Signal): List<Tone> = when (signal) {
    Signal.START -> listOf(Tone(1320, 600))
    Signal.INTERVAL -> listOf(Tone(880, 150), Tone(0, 100), Tone(880, 150))
    Signal.FINISH -> listOf(
        Tone(660, 200),
        Tone(0, 80),
        Tone(880, 200),
        Tone(0, 80),
        Tone(1100, 700),
    )
}

/** Собирает тоны в моно PCM 16 бит. Каждый тон плавно нарастает и затухает, поэтому нет щелчков. */
fun synthesize(tones: List<Tone>): ShortArray {
    val pcm = ShortArray(tones.sumOf { sampleCount(it.durationMs) })
    var offset = 0
    for (tone in tones) {
        val count = sampleCount(tone.durationMs)
        for (i in 0 until count) {
            // Первый и последний сэмплы тона равны нулю
            val envelope = min(1.0, min(i, count - 1 - i).toDouble() / FADE_SAMPLES)
            val wave = sin(2 * PI * tone.frequencyHz * i / SAMPLE_RATE_HZ)
            pcm[offset + i] = (PEAK_AMPLITUDE * envelope * wave).toInt().toShort()
        }
        offset += count
    }
    return pcm
}

fun soundDurationMs(signal: Signal): Long = tonesFor(signal).sumOf { it.durationMs.toLong() }

/** Длительность сигнала с запасом [FINISH_TAIL_MS]. Столько `Beeper` держит аудиофокус. */
fun soundWithTailMs(signal: Signal): Long = soundDurationMs(signal) + FINISH_TAIL_MS

private fun sampleCount(durationMs: Int): Int = (SAMPLE_RATE_HZ * durationMs.toLong() / 1000).toInt()
