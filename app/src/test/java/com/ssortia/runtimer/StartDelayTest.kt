package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Test

class StartDelayTest {
    @Test
    fun `ползунок даёт положение на каждые 5 секунд от 0 до 60`() {
        // 13 положений: два крайних и 11 промежуточных
        assertEquals(11, START_DELAY_SLIDER_STEPS)
    }

    @Test
    fun `значение ползунка округляется, а не обрезается`() {
        assertEquals(5, sliderToStartDelay(4.9999995f))
        assertEquals(0, sliderToStartDelay(0.2f))
    }

    @Test
    fun `значение вне диапазона прижимается`() {
        assertEquals(0, clampStartDelay(-1))
        assertEquals(60, clampStartDelay(61))
        assertEquals(30, clampStartDelay(30))
    }
}
