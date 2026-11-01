package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
    fun `сигналы различаются и финальный длиннее`() {
        val interval = synthesize(tonesFor(Signal.INTERVAL))
        val finish = synthesize(tonesFor(Signal.FINISH))

        assertFalse(interval.contentEquals(finish))
        assertTrue(durationMs(finish) > durationMs(interval))
    }

    @Test
    fun `длительность буфера считается по числу сэмплов`() {
        assertEquals(400, durationMs(synthesize(tonesFor(Signal.INTERVAL))))
        assertEquals(1260, durationMs(synthesize(tonesFor(Signal.FINISH))))
        assertEquals(0, durationMs(ShortArray(0)))
    }
}
