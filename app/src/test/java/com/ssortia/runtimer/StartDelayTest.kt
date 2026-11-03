package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartDelayTest {
    @Test
    fun `список задержек начинается с нуля, содержит 3 секунды и кончается минутой`() {
        assertEquals(listOf(0, 3, 5, 10, 15, 20, 30, 45, 60), START_DELAY_OPTIONS_SEC)
        assertEquals(START_DELAY_OPTIONS_SEC.sorted().distinct(), START_DELAY_OPTIONS_SEC)
    }

    @Test
    fun `значение по умолчанию есть в списке`() {
        assertTrue(DEFAULT_START_DELAY_SEC in START_DELAY_OPTIONS_SEC)
    }

    @Test
    fun `ползунок даёт по положению на каждое значение`() {
        // 9 положений: два крайних и 7 промежуточных
        assertEquals(7, START_DELAY_SLIDER_STEPS)
    }

    @Test
    fun `положение ползунка и значение переводятся друг в друга`() {
        START_DELAY_OPTIONS_SEC.forEachIndexed { index, sec ->
            assertEquals(index.toFloat(), startDelayToSlider(sec))
            assertEquals(sec, sliderToStartDelay(index.toFloat()))
        }
    }

    @Test
    fun `положение ползунка округляется, а не обрезается`() {
        assertEquals(3, sliderToStartDelay(0.9999995f))
        assertEquals(0, sliderToStartDelay(0.2f))
    }

    @Test
    fun `положение вне диапазона прижимается к крайнему значению`() {
        assertEquals(0, sliderToStartDelay(-1f))
        assertEquals(60, sliderToStartDelay(100f))
    }

    @Test
    fun `значение приводится к ближайшему из списка`() {
        assertEquals(5, snapStartDelay(7))
        assertEquals(10, snapStartDelay(8))
        assertEquals(30, snapStartDelay(35))
        assertEquals(45, snapStartDelay(40))
        assertEquals(60, snapStartDelay(55))
    }

    @Test
    fun `при равном расстоянии берётся меньшее значение`() {
        assertEquals(3, snapStartDelay(4))
        assertEquals(20, snapStartDelay(25))
    }

    @Test
    fun `значение вне диапазона прижимается`() {
        assertEquals(0, snapStartDelay(-1))
        assertEquals(60, snapStartDelay(61))
        assertEquals(1.0f, startDelayToSlider(2))
    }
}
