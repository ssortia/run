package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private const val MIN_SEGMENT_MS = 1000L

class TonesTest {
    @Test
    fun `длина буфера равна сумме длительностей`() {
        assertEquals(44_100, synthesize(listOf(Tone(880, 1000))).size)
        assertEquals(4_410 + 2_205, synthesize(listOf(Tone(880, 100), Tone(0, 50))).size)
    }

    @Test
    fun `пустой список даёт пустой буфер`() {
        assertEquals(0, synthesize(emptyList()).size)
    }

    @Test
    fun `в паузе только нули`() {
        val pcm = synthesize(listOf(Tone(880, 10), Tone(0, 10), Tone(880, 10)))

        val pause = pcm.slice(441 until 882)

        assertTrue(pause.all { it.toInt() == 0 })
        assertTrue(pcm.take(441).any { it.toInt() != 0 })
        assertTrue(pcm.drop(882).any { it.toInt() != 0 })
    }

    @Test
    fun `тон начинается и заканчивается нулём`() {
        val pcm = synthesize(listOf(Tone(880, 100), Tone(660, 100)))

        // Границы первого тона, стык со вторым тоном и конец буфера
        assertEquals(0, pcm[0].toInt())
        assertEquals(0, pcm[4_409].toInt())
        assertEquals(0, pcm[4_410].toInt())
        assertEquals(0, pcm.last().toInt())
    }

    @Test
    fun `амплитуда нарастает и затухает плавно`() {
        val pcm = synthesize(listOf(Tone(1000, 100)))

        for (i in 0 until FADE_SAMPLES) {
            val limit = PEAK_AMPLITUDE * i / FADE_SAMPLES + 1
            assertTrue("начало, сэмпл $i", abs(pcm[i].toInt()) <= limit)
            assertTrue("конец, сэмпл $i", abs(pcm[pcm.size - 1 - i].toInt()) <= limit)
        }
    }

    @Test
    fun `середина тона достигает полной амплитуды без перегрузки`() {
        val peak = synthesize(listOf(Tone(1000, 100))).maxOf { abs(it.toInt()) }

        assertTrue("пик $peak", peak in (PEAK_AMPLITUDE * 99 / 100)..PEAK_AMPLITUDE)
    }

    @Test
    fun `частота тона соответствует заданной`() {
        val pcm = synthesize(listOf(Tone(880, 1000)))

        // За период синус дважды меняет знак
        val crossings = pcm.toList().zipWithNext().count { (a, b) -> a < 0 != b < 0 }

        assertEquals(1760.0, crossings.toDouble(), 4.0)
    }

    @Test
    fun `обычный сигнал состоит из двух коротких тонов 880 Гц`() {
        val sounding = tonesFor(Signal.INTERVAL).filter { it.frequencyHz > 0 }

        assertEquals(listOf(880, 880), sounding.map { it.frequencyHz })
        assertEquals(sounding[0].durationMs, sounding[1].durationMs)
    }

    @Test
    fun `финальный сигнал состоит из трёх восходящих тонов и последний самый длинный`() {
        val sounding = tonesFor(Signal.FINISH).filter { it.frequencyHz > 0 }

        assertEquals(listOf(660, 880, 1100), sounding.map { it.frequencyHz })
        assertTrue(sounding[2].durationMs > sounding[0].durationMs)
        assertTrue(sounding[2].durationMs > sounding[1].durationMs)
    }

    @Test
    fun `стартовый сигнал отличается по составу от остальных`() {
        val start = tonesFor(Signal.START)

        assertEquals(listOf(Tone(1320, 600)), start)
        assertNotEquals(tonesFor(Signal.INTERVAL), start)
        assertNotEquals(tonesFor(Signal.FINISH), start)
    }

    @Test
    fun `стартовый сигнал короче минимального отрезка`() {
        // Иначе он наложится на смену отрезка при длине бега в 1 секунду
        assertTrue(soundDurationMs(Signal.START) < MIN_SEGMENT_MS)
    }

    @Test
    fun `сигналы различаются и финальный длиннее`() {
        val interval = synthesize(tonesFor(Signal.INTERVAL))
        val finish = synthesize(tonesFor(Signal.FINISH))

        assertFalse(interval.contentEquals(finish))
        assertTrue(soundDurationMs(Signal.FINISH) > soundDurationMs(Signal.INTERVAL))
    }

    @Test
    fun `длительность сигнала равна сумме его тонов и совпадает с буфером`() {
        assertEquals(600, soundDurationMs(Signal.START))
        assertEquals(400, soundDurationMs(Signal.INTERVAL))
        assertEquals(1260, soundDurationMs(Signal.FINISH))
        for (signal in Signal.entries) {
            val samples = synthesize(tonesFor(signal)).size

            assertEquals(soundDurationMs(signal), samples * 1000L / SAMPLE_RATE_HZ)
        }
    }

    @Test
    fun `в тоне короче двух затуханий огибающая не даёт скачка`() {
        // 264 сэмпла: нарастание и затухание по 220 сэмплов перекрываются
        val pcm = synthesize(listOf(Tone(1000, 6)))

        assertEquals(0, pcm.first().toInt())
        assertEquals(0, pcm.last().toInt())
        for (i in pcm.indices) {
            val fromEdge = minOf(i, pcm.size - 1 - i)
            val limit = PEAK_AMPLITUDE * fromEdge / FADE_SAMPLES + 1

            assertTrue("сэмпл $i", abs(pcm[i].toInt()) <= limit)
        }
    }

    @Test
    fun `длительность с запасом для каждого сигнала`() {
        assertEquals(900, soundWithTailMs(Signal.START))
        assertEquals(700, soundWithTailMs(Signal.INTERVAL))
        assertEquals(1560, soundWithTailMs(Signal.FINISH))
    }
}
