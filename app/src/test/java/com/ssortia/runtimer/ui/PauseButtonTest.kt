package com.ssortia.runtimer.ui

import com.ssortia.runtimer.R
import com.ssortia.runtimer.Workout
import com.ssortia.runtimer.tickAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseButtonTest {
    private val workout = Workout(runSec = 10, walkSec = 10, rounds = 2)

    @Test
    fun `подпись кнопки зависит от паузы`() {
        assertEquals(R.string.button_pause, pauseButtonRes(paused = false))
        assertEquals(R.string.button_resume, pauseButtonRes(paused = true))
    }

    @Test
    fun `пауза доступна до последней миллисекунды`() {
        assertTrue(pauseEnabled(tickAt(workout, 0, null).state))
        assertTrue(pauseEnabled(tickAt(workout, 29_999, 2).state))
        assertTrue(pauseEnabled(tickAt(workout, 15_000, 1, paused = true).state))
    }

    @Test
    fun `после финального сигнала пауза недоступна`() {
        assertFalse(pauseEnabled(tickAt(workout, 30_000, 2).state))
        assertFalse(pauseEnabled(tickAt(workout, 31_000, 2).state))
    }
}
