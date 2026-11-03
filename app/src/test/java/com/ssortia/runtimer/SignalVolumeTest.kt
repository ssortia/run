package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalVolumeTest {
    @Test
    fun `границы диапазона не меняются`() {
        assertEquals(10, clampVolume(10))
        assertEquals(100, clampVolume(100))
        assertEquals(55, clampVolume(55))
    }

    @Test
    fun `значения за границами прижимаются`() {
        assertEquals(10, clampVolume(9))
        assertEquals(10, clampVolume(0))
        assertEquals(10, clampVolume(-5))
        assertEquals(100, clampVolume(101))
    }

    @Test
    fun `крайние Int прижимаются без переполнения`() {
        assertEquals(10, clampVolume(Int.MIN_VALUE))
        assertEquals(100, clampVolume(Int.MAX_VALUE))
    }

    @Test
    fun `коэффициент громкости`() {
        assertEquals(0.1f, volumeGain(10), 1e-6f)
        assertEquals(0.55f, volumeGain(55), 1e-6f)
        assertEquals(1f, volumeGain(100), 1e-6f)
    }

    @Test
    fun `коэффициент громкости не даёт тишину и не усиливает`() {
        assertEquals(0.1f, volumeGain(0), 1e-6f)
        assertEquals(1f, volumeGain(150), 1e-6f)
    }

    @Test
    fun `значение ползунка округляется до ближайшего процента`() {
        assertEquals(35, sliderToPercent(34.999996f))
        assertEquals(35, sliderToPercent(35.00001f))
    }

    @Test
    fun `значение ползунка за границами прижимается`() {
        assertEquals(10, sliderToPercent(0f))
        assertEquals(10, sliderToPercent(9.4f))
        assertEquals(100, sliderToPercent(100.4f))
        assertEquals(100, sliderToPercent(250f))
    }

    @Test
    fun `значения ползунка совпадают с шагом`() {
        // Модель раскладки Slider: положения равномерны, и Float даёт погрешность. Сам Compose
        // здесь не проверяется, при смене его раскладки тест этого не заметит
        val positions = VOLUME_SLIDER_STEPS + 1
        val range = (MAX_VOLUME_PERCENT - MIN_VOLUME_PERCENT).toFloat()
        val percents = (0..positions).map { i ->
            sliderToPercent(MIN_VOLUME_PERCENT + range * (i.toFloat() / positions))
        }
        val expected = (MIN_VOLUME_PERCENT..MAX_VOLUME_PERCENT step VOLUME_STEP_PERCENT).toList()
        assertEquals(expected, percents)
    }
}
