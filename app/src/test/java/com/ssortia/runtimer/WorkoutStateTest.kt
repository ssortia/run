package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutStateTest {
    private val run = Position(index = 0, isRun = true, round = 1, remainingMs = 10_000)
    private val walk = Position(index = 1, isRun = false, round = 1, remainingMs = 10_000)

    // 10 с бега, 10 с ходьбы, 2 повтора: всего 40 с и 4 отрезка
    private val workout = Workout(runSec = 10, walkSec = 10, rounds = 2)

    @Test
    fun `старт не даёт сигнала`() {
        assertNull(signalFor(null, run))
    }

    @Test
    fun `внутри отрезка сигнала нет`() {
        assertNull(signalFor(1, walk))
    }

    @Test
    fun `смена отрезка даёт обычный сигнал`() {
        assertEquals(Signal.INTERVAL, signalFor(0, walk))
    }

    @Test
    fun `конец тренировки даёт финальный сигнал`() {
        assertEquals(Signal.FINISH, signalFor(1, null))
    }

    @Test
    fun `время показывает минуты и секунды с ведущим нулём`() {
        assertEquals("2:00", formatTime(120_000))
        assertEquals("1:05", formatTime(65_000))
        assertEquals("99:59", formatTime(5_999_000))
    }

    @Test
    fun `секунды округляются вверх`() {
        assertEquals("2:00", formatTime(119_001))
        assertEquals("1:59", formatTime(119_000))
        assertEquals("0:02", formatTime(1_250))
    }

    @Test
    fun `вблизи нуля время не обнуляется раньше конца`() {
        assertEquals("0:01", formatTime(1_000))
        assertEquals("0:01", formatTime(1))
        assertEquals("0:00", formatTime(0))
        assertEquals("0:00", formatTime(-1))
    }

    @Test
    fun `общая длительность равна сумме всех отрезков`() {
        assertEquals(40_000L, workout.totalMs)
        assertEquals(1_680_000L, WorkoutSettings().toWorkout().totalMs)
    }

    @Test
    fun `прогресс растёт от нуля до единицы`() {
        assertEquals(0f, workout.progressAt(0), 0f)
        assertEquals(0.25f, workout.progressAt(10_000), 0f)
        assertEquals(0.5f, workout.progressAt(20_000), 0f)
        assertEquals(1f, workout.progressAt(40_000), 0f)
    }

    @Test
    fun `прогресс вблизи нуля положительный и мал`() {
        assertEquals(0.000025f, workout.progressAt(1), 1e-7f)
    }

    @Test
    fun `прогресс не выходит за границы`() {
        assertEquals(0f, workout.progressAt(-1), 0f)
        assertEquals(1f, workout.progressAt(40_001), 0f)
        assertEquals(1f, workout.progressAt(Long.MAX_VALUE), 0f)
    }

    @Test
    fun `цикл с шагом 250 мс даёт три обычных сигнала и один финальный`() {
        val signals = mutableListOf<Signal>()
        var previousIndex: Int? = null
        var elapsed = 0L

        while (true) {
            val position = workout.at(elapsed)
            signalFor(previousIndex, position)?.let { signals += it }
            if (position == null) break
            previousIndex = position.index
            elapsed += 250
        }

        assertEquals(
            listOf(Signal.INTERVAL, Signal.INTERVAL, Signal.INTERVAL, Signal.FINISH),
            signals,
        )
    }
}
