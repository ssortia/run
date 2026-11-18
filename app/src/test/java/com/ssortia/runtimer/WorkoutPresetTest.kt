package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPresetTest {
    @Test
    fun `недели идут с 1 по 10 без пропусков`() {
        assertEquals((1..10).toList(), WORKOUT_PRESETS.map { it.week })
    }

    @Test
    fun `каждая неделя проходит проверку`() {
        WORKOUT_PRESETS.forEach { assertNull("неделя ${it.week}", it.settings.validate()) }
    }

    @Test
    fun `сервис принимает каждую неделю`() {
        WORKOUT_PRESETS.forEach {
            val workout = it.settings.toWorkout()
            assertEquals(
                "неделя ${it.week}",
                workout,
                workoutOrNull(workout.runSec, workout.walkSec, workout.rounds),
            )
        }
    }

    @Test
    fun `итоговая длительность недель совпадает с программой`() {
        // Секунды вписаны числами из таблицы US-13 в USER_STORIES.md, а не вычислены из WORKOUT_PRESETS
        val expectedSec = listOf(
            19 * 60, 22 * 60, 23 * 60, 24 * 60, 25 * 60,
            28 * 60, 28 * 60, 30 * 60, 31 * 60, 30 * 60,
        )

        assertEquals(
            expectedSec.map { it * 1000L },
            WORKOUT_PRESETS.map { it.settings.toWorkout().totalMs },
        )
    }

    @Test
    fun `общее время каждой недели — целые минуты`() {
        WORKOUT_PRESETS.forEach {
            assertEquals("неделя ${it.week}", 0L, it.settings.toWorkout().totalMs % 60_000)
        }
    }

    @Test
    fun `отрезки кратны 30 секундам`() {
        WORKOUT_PRESETS.forEach {
            val workout = it.settings.toWorkout()
            assertEquals("бег недели ${it.week}", 0, workout.runSec % 30)
            assertEquals("шаг недели ${it.week}", 0, workout.walkSec % 30)
        }
    }

    @Test
    fun `бег за тренировку не убывает, а отрезок бега растёт каждую неделю`() {
        val workouts = WORKOUT_PRESETS.map { it.settings.toWorkout() }
        workouts.zipWithNext().forEach { (a, b) ->
            assertTrue("$a -> $b", b.runSec * b.rounds >= a.runSec * a.rounds)
            assertTrue("$a -> $b", b.runSec > a.runSec)
        }
    }

    @Test
    fun `бег за тренировку растёт не больше чем на 5 минут, а после второй недели не больше чем на 3`() {
        val runMin = WORKOUT_PRESETS.map { it.settings.toWorkout().let { w -> w.runSec * w.rounds / 60 } }
        assertEquals(listOf(7, 12, 15, 18, 21, 24, 26, 28, 30, 30), runMin)
        runMin.zipWithNext().drop(1).forEach { (a, b) -> assertTrue("$a -> $b", b - a <= 3) }
    }

    @Test
    fun `шаг не убывает до недели 9 и сокращается не больше одного раза`() {
        val walks = WORKOUT_PRESETS.filter { it.settings.rounds > 1 }.map { it.settings.toWorkout().walkSec }
        assertEquals(1, walks.zipWithNext().count { (a, b) -> b < a })
        walks.zipWithNext().forEach { (a, b) -> assertTrue("$a -> $b", b <= a) }
    }

    @Test
    fun `тройки недель не повторяются, в том числе с прежней программой`() {
        val current = WORKOUT_PRESETS.map { it.settings.toWorkout() }
        val all = current + PREVIOUS_PRESET_WORKOUTS.map { it.second }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `точное совпадение возвращает свою неделю`() {
        WORKOUT_PRESETS.forEach { assertEquals(it.week, presetWeekOf(it.settings)) }
    }

    @Test
    fun `значения по умолчанию дают неделю 1`() {
        assertEquals(1, presetWeekOf(WorkoutSettings()))
    }

    @Test
    fun `отличие в секундах или повторах даёт null`() {
        assertNull(presetWeekOf(WorkoutSettings(runMin = 2, runSec = 1, walkMin = 2, walkSec = 0, rounds = 7)))
        assertNull(presetWeekOf(WorkoutSettings(runMin = 2, runSec = 0, walkMin = 2, walkSec = 0, rounds = 7)))
        assertNull(presetWeekOf(WorkoutSettings(runMin = 30, runSec = 0, walkMin = 0, walkSec = 30, rounds = 1)))
    }
}
