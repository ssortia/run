package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutTest {
    // 10 с бега, 5 с ходьбы, 3 повтора: всего 40 с и 5 отрезков
    private val workout = Workout(runSec = 10, walkSec = 5, rounds = 3)

    @Test
    fun `старт попадает в первый отрезок бега`() {
        assertEquals(
            Position(index = 0, isRun = true, round = 1, remainingMs = 10_000),
            workout.at(0),
        )
    }

    @Test
    fun `бег сменяется ходьбой ровно на границе`() {
        assertEquals(
            Position(index = 0, isRun = true, round = 1, remainingMs = 1),
            workout.at(9_999),
        )
        assertEquals(
            Position(index = 1, isRun = false, round = 1, remainingMs = 5_000),
            workout.at(10_000),
        )
    }

    @Test
    fun `ходьба сменяется бегом следующего повтора`() {
        assertEquals(
            Position(index = 1, isRun = false, round = 1, remainingMs = 1),
            workout.at(14_999),
        )
        assertEquals(
            Position(index = 2, isRun = true, round = 2, remainingMs = 10_000),
            workout.at(15_000),
        )
    }

    @Test
    fun `номер повтора считается с единицы и растёт после ходьбы`() {
        val rounds = listOf(0L, 14_999, 15_000, 29_999, 30_000, 39_999).map { workout.at(it)?.round }

        assertEquals(listOf(1, 1, 2, 2, 3, 3), rounds)
    }

    @Test
    fun `остаток времени уменьшается внутри отрезка`() {
        assertEquals(7_500L, workout.at(2_500)?.remainingMs)
        assertEquals(2_000L, workout.at(13_000)?.remainingMs)
        assertEquals(4_250L, workout.at(35_750)?.remainingMs)
    }

    @Test
    fun `последняя миллисекунда ещё внутри последнего отрезка`() {
        assertEquals(
            Position(index = 4, isRun = true, round = 3, remainingMs = 1),
            workout.at(39_999),
        )
    }

    @Test
    fun `в момент окончания позиции нет`() {
        assertNull(workout.at(40_000))
    }

    @Test
    fun `после окончания позиции нет`() {
        assertNull(workout.at(40_001))
        assertNull(workout.at(Long.MAX_VALUE))
    }

    @Test
    fun `один повтор состоит из одного отрезка бега`() {
        val single = Workout(runSec = 3, walkSec = 2, rounds = 1)

        assertEquals(
            Position(index = 0, isRun = true, round = 1, remainingMs = 3_000),
            single.at(0),
        )
        assertEquals(
            Position(index = 0, isRun = true, round = 1, remainingMs = 1),
            single.at(2_999),
        )
        assertNull(single.at(3_000))
    }

    @Test
    fun `верные параметры дают тренировку`() {
        assertEquals(Workout(1, 1, 1), workoutOrNull(runSec = 1, walkSec = 1, rounds = 1))
    }

    @Test
    fun `нулевые и отрицательные параметры отклоняются`() {
        assertNull(workoutOrNull(runSec = 0, walkSec = 1, rounds = 1))
        assertNull(workoutOrNull(runSec = 1, walkSec = 0, rounds = 1))
        assertNull(workoutOrNull(runSec = 1, walkSec = 1, rounds = 0))
        assertNull(workoutOrNull(runSec = -1, walkSec = 1, rounds = 1))
        assertNull(workoutOrNull(runSec = 1, walkSec = -1, rounds = 1))
        assertNull(workoutOrNull(runSec = 1, walkSec = 1, rounds = -1))
    }

    @Test
    fun `нулевая задержка допустима`() {
        assertEquals(0L, startDelayMsOrNull(0))
    }

    @Test
    fun `задержка переводится в миллисекунды`() {
        assertEquals(60_000L, startDelayMsOrNull(60))
    }

    @Test
    fun `отрицательная задержка отклоняется`() {
        assertNull(startDelayMsOrNull(-1))
    }

    @Test
    fun `таймаут wake lock покрывает тренировку, задержку и запас`() {
        assertEquals(1_560_000L + 60_000L + 60_000L, wakeLockTimeoutMs(1_560_000, 60_000))
    }

    @Test
    fun `без задержки таймаут wake lock покрывает тренировку и запас`() {
        assertEquals(1_560_000L + 60_000L, wakeLockTimeoutMs(1_560_000, 0))
    }
}
