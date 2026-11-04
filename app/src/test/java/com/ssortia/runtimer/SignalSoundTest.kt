package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalSoundTest {
    @Test
    fun `старт звучит так же долго, как смена отрезка`() {
        assertEquals(soundDurationMs(Signal.INTERVAL), soundDurationMs(Signal.START))
    }

    @Test
    fun `финальный сигнал длиннее обычного`() {
        assertTrue(soundDurationMs(Signal.FINISH) > soundDurationMs(Signal.INTERVAL))
    }

    @Test
    fun `длительность сигнала равна длительности его звука`() {
        assertEquals(370, soundDurationMs(Signal.START))
        assertEquals(370, soundDurationMs(Signal.INTERVAL))
        assertEquals(1240, soundDurationMs(Signal.FINISH))
    }

    @Test
    fun `длительность с запасом для каждого сигнала`() {
        assertEquals(670, soundWithTailMs(Signal.START))
        assertEquals(670, soundWithTailMs(Signal.INTERVAL))
        assertEquals(1540, soundWithTailMs(Signal.FINISH))
    }

    @Test
    fun `музыка приглушается за секунду до сигнала`() {
        assertFalse(shouldDuck(1_001, duckMusic = true))
        assertTrue(shouldDuck(1_000, duckMusic = true))
        assertTrue(shouldDuck(1, duckMusic = true))
        assertTrue(shouldDuck(0, duckMusic = true))
        // В конце отсчёта время до старта может уйти в минус
        assertTrue(shouldDuck(-1, duckMusic = true))
    }

    @Test
    fun `на паузе музыка не приглушается`() {
        assertFalse(shouldDuck(500, duckMusic = true, paused = true))
        assertTrue(shouldDuck(500, duckMusic = true, paused = false))
    }

    @Test
    fun `после продолжения музыка приглушается, если до сигнала не больше секунды`() {
        // Бег 60 с, шаг 30 с, 2 повтора: сигналы на 60 с, 90 с и финал на 150 с
        val workout = Workout(runSec = 60, walkSec = 30, rounds = 2)

        assertFalse(shouldDuckAfterResume(workout, 58_999, duckMusic = true))
        assertTrue(shouldDuckAfterResume(workout, 59_000, duckMusic = true))
        assertTrue(shouldDuckAfterResume(workout, 59_500, duckMusic = true))
        // Шаг кончается сигналом так же, как бег
        assertTrue(shouldDuckAfterResume(workout, 89_500, duckMusic = true))
        assertTrue(shouldDuckAfterResume(workout, 149_500, duckMusic = true))
        assertFalse(shouldDuckAfterResume(workout, 60_000, duckMusic = true))
    }

    @Test
    fun `после конца тренировки музыка не приглушается`() {
        val workout = Workout(runSec = 60, walkSec = 30, rounds = 2)

        assertFalse(shouldDuckAfterResume(workout, 150_000, duckMusic = true))
        assertFalse(shouldDuckAfterResume(workout, 151_000, duckMusic = true))
    }

    @Test
    fun `при выключенной настройке музыка заранее не приглушается`() {
        assertFalse(shouldDuck(0, duckMusic = false))
        assertFalse(shouldDuck(-1, duckMusic = false))
    }

    @Test
    fun `при выключенной настройке музыка не приглушается и после продолжения`() {
        val workout = Workout(runSec = 60, walkSec = 30, rounds = 2)

        // В этих точках при включённой настройке музыка приглушилась бы
        assertFalse(shouldDuckAfterResume(workout, 59_000, duckMusic = false))
        assertFalse(shouldDuckAfterResume(workout, 59_500, duckMusic = false))
        assertFalse(shouldDuckAfterResume(workout, 89_500, duckMusic = false))
        assertFalse(shouldDuckAfterResume(workout, 149_500, duckMusic = false))
    }
}
