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
        assertEquals(1f, volumeGain(100), 1e-6f)
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
}
