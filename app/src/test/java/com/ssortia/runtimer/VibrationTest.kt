package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrationTest {
    @Test
    fun `паттерны начинаются без задержки`() {
        for (signal in Signal.entries) {
            assertEquals("$signal", 0L, vibrationFor(signal).first())
        }
    }

    @Test
    fun `паттерны заканчиваются вибрацией`() {
        // Чётная длина: последний элемент стоит на нечётном месте, то есть это вибрация, а не ожидание
        for (signal in Signal.entries) {
            val pattern = vibrationFor(signal)
            assertEquals("$signal", 0, pattern.size % 2)
            assertTrue("$signal", pattern.last() > 0)
        }
    }

    @Test
    fun `все отрезки паттернов положительны, кроме начального ожидания`() {
        for (signal in Signal.entries) {
            assertTrue("$signal", vibrationFor(signal).drop(1).all { it > 0 })
        }
    }

    @Test
    fun `сигналы различаются паттерном`() {
        assertFalse(vibrationFor(Signal.FINISH).contentEquals(vibrationFor(Signal.INTERVAL)))
        assertFalse(vibrationFor(Signal.START).contentEquals(vibrationFor(Signal.INTERVAL)))
        assertFalse(vibrationFor(Signal.START).contentEquals(vibrationFor(Signal.FINISH)))
    }

    @Test
    fun `длительность равна сумме отрезков паттерна`() {
        assertEquals(600L, vibrationDurationMs(Signal.START))
        assertEquals(750L, vibrationDurationMs(Signal.INTERVAL))
        assertEquals(1600L, vibrationDurationMs(Signal.FINISH))
    }

    @Test
    fun `финальная вибрация длиннее обычной`() {
        assertTrue(vibrationDurationMs(Signal.FINISH) > vibrationDurationMs(Signal.INTERVAL))
    }
}
