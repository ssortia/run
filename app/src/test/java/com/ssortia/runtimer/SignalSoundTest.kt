package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalSoundTest {
    @Test
    fun `старт звучит так же долго, как смена отрезка`() {
        assertEquals(soundDurationMs(Signal.INTERVAL), soundDurationMs(Signal.START))
    }

    @Test
    fun `финальный сигнал длиннее обычного`() {
        assertTrue(soundDurationMs(Signal.FINISH) > soundDurationMs(Signal.INTERVAL))
    }

    @Test
    fun `длительность сигнала равна длительности его звука`() {
        assertEquals(370, soundDurationMs(Signal.START))
        assertEquals(370, soundDurationMs(Signal.INTERVAL))
        assertEquals(1240, soundDurationMs(Signal.FINISH))
    }

    @Test
    fun `длительность с запасом для каждого сигнала`() {
        assertEquals(670, soundWithTailMs(Signal.START))
        assertEquals(670, soundWithTailMs(Signal.INTERVAL))
        assertEquals(1540, soundWithTailMs(Signal.FINISH))
    }

    @Test
    fun `музыка приглушается за секунду до сигнала`() {
        assertFalse(shouldDuck(1_001))
        assertTrue(shouldDuck(1_000))
        assertTrue(shouldDuck(1))
        assertTrue(shouldDuck(0))
    }

    @Test
    fun `на паузе музыка не приглушается`() {
        assertFalse(shouldDuck(500, paused = true))
        assertTrue(shouldDuck(500, paused = false))
    }
}
