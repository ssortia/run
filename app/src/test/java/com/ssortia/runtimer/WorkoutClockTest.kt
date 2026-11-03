package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutClockTest {
    private val start = 1_000L
    private fun clock() = WorkoutClock(totalMs = 10_000, startedAt = start)

    @Test
    fun `без пауз время идёт от старта`() {
        val c = clock()
        assertFalse(c.isPaused)
        assertEquals(0L, c.elapsedAt(start))
        assertEquals(2_500L, c.elapsedAt(start + 2_500))
        assertEquals(7_500L, c.remainingAt(start + 2_500))
    }

    @Test
    fun `на паузе время замирает`() {
        val c = clock()
        c.pause(start + 3_000)
        assertTrue(c.isPaused)
        assertEquals(3_000L, c.elapsedAt(start + 3_000))
        assertEquals(3_000L, c.elapsedAt(start + 60_000))
        assertEquals(7_000L, c.remainingAt(start + 60_000))
    }

    @Test
    fun `после продолжения время идёт с того же места`() {
        val c = clock()
        c.pause(start + 3_000)
        c.resume(start + 8_000)
        assertFalse(c.isPaused)
        assertEquals(3_000L, c.elapsedAt(start + 8_000))
        assertEquals(4_000L, c.elapsedAt(start + 9_000))
    }

    @Test
    fun `несколько пауз подряд складываются`() {
        val c = clock()
        c.pause(start + 1_000)
        c.resume(start + 2_000)
        c.pause(start + 3_000)
        c.resume(start + 6_000)
        // 10000 мс минус 1000 и 3000 мс пауз
        assertEquals(6_000L, c.elapsedAt(start + 10_000))
    }

    @Test
    fun `повторная пауза не сдвигает момент паузы`() {
        val c = clock()
        c.pause(start + 2_000)
        c.pause(start + 5_000)
        assertEquals(2_000L, c.elapsedAt(start + 9_000))
        c.resume(start + 9_000)
        assertEquals(3_000L, c.elapsedAt(start + 10_000))
    }

    @Test
    fun `повторное продолжение ничего не меняет`() {
        val c = clock()
        c.pause(start + 2_000)
        c.resume(start + 4_000)
        c.resume(start + 7_000)
        assertEquals(7_000L, c.elapsedAt(start + 9_000))
    }

    @Test
    fun `продолжение без паузы ничего не меняет`() {
        val c = clock()
        c.resume(start + 4_000)
        assertFalse(c.isPaused)
        assertEquals(5_000L, c.elapsedAt(start + 5_000))
    }

    @Test
    fun `пауза ровно в конце тренировки игнорируется`() {
        val c = clock()
        c.pause(start + 10_000)
        assertFalse(c.isPaused)
        assertEquals(11_000L, c.elapsedAt(start + 11_000))
    }

    @Test
    fun `пауза после конца тренировки игнорируется`() {
        val c = clock()
        c.pause(start + 12_000)
        assertFalse(c.isPaused)
    }

    @Test
    fun `пауза за миллисекунду до конца срабатывает`() {
        val c = clock()
        c.pause(start + 9_999)
        assertTrue(c.isPaused)
        assertEquals(1L, c.remainingAt(start + 20_000))
    }

    @Test
    fun `остаток не уходит ниже нуля`() {
        val c = clock()
        assertEquals(0L, c.remainingAt(start + 10_000))
        assertEquals(0L, c.remainingAt(start + 50_000))
    }

    @Test
    fun `пауза до конца сравнивает время без пауз, а не от старта`() {
        val c = clock()
        c.pause(start + 3_000)
        c.resume(start + 8_000)
        // От старта прошло 12 с, без пауз только 7 с
        c.pause(start + 12_000)
        assertTrue(c.isPaused)
        assertEquals(7_000L, c.elapsedAt(start + 20_000))
    }

    @Test
    fun `пауза ровно в конце после продолжения игнорируется`() {
        val c = clock()
        c.pause(start + 3_000)
        c.resume(start + 8_000)
        c.pause(start + 15_000)
        assertFalse(c.isPaused)
    }

    @Test
    fun `таймаут wake lock после продолжения покрывает остаток и запас`() {
        val c = clock()
        c.pause(start + 3_000)
        c.resume(start + 60_000)
        assertEquals(7_000L + WAKE_LOCK_MARGIN_MS, wakeLockTimeoutMs(c.remainingAt(start + 60_000), delayMs = 0))
    }
}
