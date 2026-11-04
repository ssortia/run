package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Сверяет звуки в res/raw с константами: по ним держится фокус и ждёт финал. */
class SignalFilesTest {
    // Unit-тесты запускаются из каталога модуля app
    private fun read(name: String): ShortArray {
        val pcm = decodeWav(File("src/main/res/raw/$name.wav").readBytes())
        assertNotNull("$name: формат должен быть PCM 16 бит моно $SAMPLE_RATE_HZ Гц", pcm)
        return pcm!!
    }

    private fun durationMs(pcm: ShortArray): Long = pcm.size * 1000L / SAMPLE_RATE_HZ

    @Test
    fun `длительность звука смены отрезка совпадает с константой`() {
        assertEquals(INTERVAL_SOUND_MS, durationMs(read("signal_interval")))
    }

    @Test
    fun `длительность звука финала совпадает с константой`() {
        assertEquals(FINISH_SOUND_MS, durationMs(read("signal_finish")))
    }

    @Test
    fun `длительность звука отсчёта совпадает с константой`() {
        assertEquals(COUNTDOWN_SOUND_MS, durationMs(read("signal_countdown")))
    }

    @Test
    fun `длительность звука старта совпадает с константой`() {
        assertEquals(START_SOUND_MS, durationMs(read("signal_start")))
    }

    @Test
    fun `звук смены отрезка короче минимального отрезка`() {
        assertTrue(INTERVAL_SOUND_MS < 1000)
    }

    @Test
    fun `звук отсчёта короче секунды между сигналами 2 и 1`() {
        assertTrue(durationMs(read("signal_countdown")) < 1000)
    }

    @Test
    fun `звуки не начинаются и не обрываются щелчком`() {
        for (name in listOf("signal_countdown", "signal_start", "signal_interval", "signal_finish")) {
            val pcm = read(name)

            assertTrue("$name: начало", abs(pcm.first().toInt()) < 500)
            assertTrue("$name: конец", abs(pcm.last().toInt()) < 500)
        }
    }
}
