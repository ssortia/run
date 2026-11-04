package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSummaryTest {
    private val labels = SummaryLabels(run = "бег", walk = "шаг", min = "мин")

    private fun summary(workout: Workout) =
        workoutSummaryParts(workout, "${segmentCount(workout)} отр.", labels).joinToString(" · ")

    private fun week(week: Int) = WORKOUT_PRESETS.first { it.week == week }.settings.toWorkout()

    @Test
    fun wholeMinutes() {
        val workout = week(2)
        assertEquals(
            listOf("11 отрезков", "бег 12 мин", "шаг 10 мин"),
            workoutSummaryParts(workout, "11 отрезков", labels),
        )
        assertEquals("11 отр. · бег 12 мин · шаг 10 мин", summary(workout))
    }

    @Test
    fun minutesAndSeconds() {
        assertEquals("5 отр. · бег 13:30 · шаг 4 мин", summary(Workout(runSec = 270, walkSec = 120, rounds = 3)))
    }

    @Test
    fun secondsAddingUpToWholeMinutes() {
        assertEquals("7 отр. · бег 18 мин · шаг 6 мин", summary(week(4)))
    }

    @Test
    fun walkWithSeconds() {
        assertEquals("5 отр. · бег 21 мин · шаг 3 мин", summary(week(5)))
        assertEquals("5 отр. · бег 3 мин · шаг 1:30", summary(Workout(runSec = 60, walkSec = 45, rounds = 3)))
    }

    @Test
    fun singleRoundHasNoWalk() {
        assertEquals("1 отр. · бег 30 мин", summary(week(10)))
        assertEquals("1 отр. · бег 5 мин", summary(Workout(runSec = 300, walkSec = 120, rounds = 1)))
    }

    @Test
    fun hourAndMore() {
        assertEquals("9 отр. · бег 1:02:30 · шаг 4 мин", summary(Workout(runSec = 750, walkSec = 60, rounds = 5)))
        assertEquals("11 отр. · бег 90 мин · шаг 5 мин", summary(Workout(runSec = 900, walkSec = 60, rounds = 6)))
    }

    @Test
    fun segmentCount() {
        assertEquals(1, segmentCount(Workout(runSec = 60, walkSec = 0, rounds = 1)))
        assertEquals(3, segmentCount(Workout(runSec = 60, walkSec = 60, rounds = 2)))
        assertEquals(13, segmentCount(week(1)))
        assertEquals(11, segmentCount(week(2)))
    }

    @Test
    fun description() {
        val workout = week(2)
        assertEquals(
            "Вся тренировка, 22:00, 11 отрезков, бег 12 мин, шаг 10 мин",
            summaryDescription(
                title = "Вся тренировка",
                time = formatTotal(workout.totalMs),
                parts = workoutSummaryParts(workout, "11 отрезков", labels),
            ),
        )
    }
}
