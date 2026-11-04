package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalVolumeTest {
    @Test
    fun `значения на шаге не меняются`() {
        assertEquals(0, snapVolume(0))
        assertEquals(10, snapVolume(10))
        assertEquals(50, snapVolume(50))
        assertEquals(100, snapVolume(100))
    }

    @Test
    fun `старые значения с шагом 5 приводятся к ближайшему шагу 10 вверх`() {
        assertEquals(20, snapVolume(15))
        assertEquals(40, snapVolume(35))
        assertEquals(60, snapVolume(55))
        assertEquals(100, snapVolume(95))
    }

    @Test
    fun `значения между шагами округляются к ближайшему`() {
        assertEquals(30, snapVolume(34))
        assertEquals(0, snapVolume(4))
        assertEquals(10, snapVolume(5))
    }

    @Test
    fun `значения за границами прижимаются`() {
        assertEquals(0, snapVolume(-5))
        assertEquals(100, snapVolume(101))
        assertEquals(0, snapVolume(Int.MIN_VALUE))
        assertEquals(100, snapVolume(Int.MAX_VALUE))
    }

    @Test
    fun `коэффициент громкости`() {
        assertEquals(0f, volumeGain(0), 1e-6f)
        assertEquals(0.1f, volumeGain(10), 1e-6f)
        assertEquals(0.6f, volumeGain(55), 1e-6f)
        assertEquals(1f, volumeGain(100), 1e-6f)
        assertEquals(1f, volumeGain(150), 1e-6f)
    }

    @Test
    fun `значение ползунка округляется до шага`() {
        assertEquals(40, sliderToPercent(39.999996f))
        assertEquals(40, sliderToPercent(40.00001f))
        assertEquals(0, sliderToPercent(0.3f))
    }

    @Test
    fun `значение ползунка за границами прижимается`() {
        assertEquals(0, sliderToPercent(-3f))
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
        assertEquals(9, VOLUME_SLIDER_STEPS)
    }

    @Test
    fun `звук выключен только при нуле`() {
        assertFalse(isSoundOn(0))
        assertFalse(isSoundOn(4))
        assertTrue(isSoundOn(10))
        assertTrue(isSoundOn(100))
    }

    @Test
    fun `при выключенном звуке предупреждений о звуке нет`() {
        assertFalse(soundFailedWarning(soundOn = false, beeperReady = false))
        assertFalse(soundMutedWarning(soundOn = false, mediaMuted = true))
    }

    @Test
    fun `при включённом звуке предупреждения по состоянию`() {
        assertTrue(soundFailedWarning(soundOn = true, beeperReady = false))
        assertFalse(soundFailedWarning(soundOn = true, beeperReady = true))
        assertTrue(soundMutedWarning(soundOn = true, mediaMuted = true))
        assertFalse(soundMutedWarning(soundOn = true, mediaMuted = false))
    }

    @Test
    fun `подсказка под ползунком громкости`() {
        assertNull(volumeHint(10, vibration = true, vibrationOnly = "в", noSignals = "н"))
        assertNull(volumeHint(10, vibration = false, vibrationOnly = "в", noSignals = "н"))
        assertEquals("в", volumeHint(0, vibration = true, vibrationOnly = "в", noSignals = "н"))
        assertEquals("н", volumeHint(0, vibration = false, vibrationOnly = "в", noSignals = "н"))
    }
}
