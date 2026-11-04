package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutSummaryTest {
    private val labels = SummaryLabels(run = "бег", walk = "шаг", min = "мин")

    private fun summary(settings: WorkoutSettings): WorkoutSummaryText? =
        workoutSummaryText(settings, settings.validate(), labels) { "$it отр." }

    private fun summary(workout: Workout): String = workoutSummaryParts(workout, "${segmentCount(workout)} отр.", labels)
        .let { WorkoutSummaryText(time = "", parts = it).text }

    private fun week(week: Int) = WORKOUT_PRESETS.first { it.week == week }.settings

    @Test
    fun `целые минуты`() {
        assertEquals(
            listOf("11 отрезков", "бег 12 мин", "шаг 10 мин"),
            workoutSummaryParts(week(2).toWorkout(), "11 отрезков", labels),
        )
        assertEquals("11 отр. · бег 12 мин · шаг 10 мин", summary(week(2))?.text)
    }

    @Test
    fun `минуты и секунды`() {
        assertEquals("5 отр. · бег 13:30 · шаг 4 мин", summary(Workout(runSec = 270, walkSec = 120, rounds = 3)))
    }

    @Test
    fun `секунды складываются в целые минуты`() {
        assertEquals("7 отр. · бег 18 мин · шаг 6 мин", summary(week(4))?.text)
    }

    @Test
    fun `шаг с секундами`() {
        assertEquals("5 отр. · бег 21 мин · шаг 3 мин", summary(week(5))?.text)
        assertEquals("5 отр. · бег 3 мин · шаг 1:30", summary(Workout(runSec = 60, walkSec = 45, rounds = 3)))
    }

    @Test
    fun `при одном повторе шага нет`() {
        assertEquals("1 отр. · бег 30 мин", summary(week(10))?.text)
        assertEquals("1 отр. · бег 5 мин", summary(Workout(runSec = 300, walkSec = 120, rounds = 1)))
    }

    @Test
    fun `меньше минуты`() {
        assertEquals("1 отр. · бег 0:30", summary(Workout(runSec = 30, walkSec = 0, rounds = 1)))
    }

    @Test
    fun `час и больше`() {
        assertEquals("9 отр. · бег 1:02:30 · шаг 4 мин", summary(Workout(runSec = 750, walkSec = 60, rounds = 5)))
        assertEquals("11 отр. · бег 90 мин · шаг 5 мин", summary(Workout(runSec = 900, walkSec = 60, rounds = 6)))
    }

    @Test
    fun `число отрезков`() {
        assertEquals(1, segmentCount(Workout(runSec = 60, walkSec = 0, rounds = 1)))
        assertEquals(3, segmentCount(Workout(runSec = 60, walkSec = 60, rounds = 2)))
        assertEquals(13, segmentCount(week(1).toWorkout()))
        assertEquals(11, segmentCount(week(2).toWorkout()))
    }

    @Test
    fun `время итога равно длительности тренировки`() {
        assertEquals("22:00", summary(week(2))?.time)
        assertEquals("30:00", summary(week(10))?.time)
    }

    @Test
    fun `описание для TalkBack при многих повторах`() {
        assertEquals(
            "Вся тренировка, 22:00, 11 отр., бег 12 мин, шаг 10 мин",
            summary(week(2))?.description("Вся тренировка"),
        )
    }

    @Test
    fun `описание для TalkBack при одном повторе`() {
        assertEquals("Вся тренировка, 30:00, 1 отр., бег 30 мин", summary(week(10))?.description("Вся тренировка"))
    }

    @Test
    fun `при неверных настройках итога нет`() {
        assertNull(summary(WorkoutSettings(runMin = 0, runSec = 0, walkMin = 2, walkSec = 0, rounds = 3)))
        assertNull(summary(WorkoutSettings(runMin = 1, runSec = 0, walkMin = 0, walkSec = 0, rounds = 3)))
        assertNull(summary(WorkoutSettings(runMin = 1, runSec = 60, walkMin = 1, walkSec = 0, rounds = 3)))
    }

    @Test
    fun `нулевой шаг при одном повторе допустим`() {
        assertEquals(
            WorkoutSummaryText(time = "30:00", parts = listOf("1 отр.", "бег 30 мин")),
            summary(WorkoutSettings(runMin = 30, runSec = 0, walkMin = 0, walkSec = 0, rounds = 1)),
        )
    }
}
