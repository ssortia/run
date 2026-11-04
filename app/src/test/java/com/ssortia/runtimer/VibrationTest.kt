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
        assertFalse(vibrationFor(Signal.COUNTDOWN).contentEquals(vibrationFor(Signal.START)))
        assertFalse(vibrationFor(Signal.COUNTDOWN).contentEquals(vibrationFor(Signal.INTERVAL)))
    }

    @Test
    fun `вибрация отсчёта короче старта и заканчивается до следующего сигнала`() {
        assertTrue(vibrationDurationMs(Signal.COUNTDOWN) < vibrationDurationMs(Signal.START))
        assertTrue(vibrationDurationMs(Signal.COUNTDOWN) < 1_000)
    }

    @Test
    fun `длительность равна сумме отрезков паттерна`() {
        for (signal in Signal.entries) {
            assertEquals("$signal", vibrationFor(signal).sum(), vibrationDurationMs(signal))
        }
    }

    @Test
    fun `длительности паттернов`() {
        assertEquals(150L, vibrationDurationMs(Signal.COUNTDOWN))
        assertEquals(600L, vibrationDurationMs(Signal.START))
        assertEquals(400L, vibrationDurationMs(Signal.INTERVAL))
        assertEquals(1600L, vibrationDurationMs(Signal.FINISH))
    }

    @Test
    fun `финальная вибрация длиннее обычной`() {
        assertTrue(vibrationDurationMs(Signal.FINISH) > vibrationDurationMs(Signal.INTERVAL))
    }

    @Test
    fun `отсчёт, старт и смена отрезка один импульс, финал три`() {
        // Импульсы стоят на нечётных местах паттерна
        fun pulses(signal: Signal) = vibrationFor(signal).size / 2

        assertEquals(1, pulses(Signal.COUNTDOWN))
        assertEquals(1, pulses(Signal.START))
        assertEquals(1, pulses(Signal.INTERVAL))
        assertEquals(3, pulses(Signal.FINISH))
    }

    @Test
    fun `ожидание после финала покрывает звук и вибрацию с запасом`() {
        assertTrue(finishWaitMs() >= vibrationDurationMs(Signal.FINISH) + FINISH_TAIL_MS)
        assertTrue(finishWaitMs() >= soundWithTailMs(Signal.FINISH))
        assertEquals(1_900L, finishWaitMs())
    }
}
