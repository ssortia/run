package com.ssortia.runtimer.ui

import com.ssortia.runtimer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundWarningTest {
    @Test
    fun `без сбоя и с громкостью предупреждения нет`() {
        assertNull(soundWarningRes(soundFailed = false, soundMuted = false))
    }

    @Test
    fun `сбой звука даёт предупреждение о недоступных сигналах`() {
        assertEquals(R.string.warning_no_sound, soundWarningRes(soundFailed = true, soundMuted = false))
    }

    @Test
    fun `нулевая громкость даёт предупреждение о громкости`() {
        assertEquals(R.string.warning_muted, soundWarningRes(soundFailed = false, soundMuted = true))
    }

    @Test
    fun `сбой звука важнее нулевой громкости`() {
        // Прибавление громкости не вернёт неподготовленные сигналы
        assertEquals(R.string.warning_no_sound, soundWarningRes(soundFailed = true, soundMuted = true))
    }
}
