package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutPresetTest {
    @Test
    fun `недели идут с 1 по 11 без пропусков`() {
        assertEquals((1..11).toList(), WORKOUT_PRESETS.map { it.week })
    }

    @Test
    fun `каждая неделя проходит проверку`() {
        WORKOUT_PRESETS.forEach { assertNull("неделя ${it.week}", it.settings.validate()) }
    }

    @Test
    fun `итоговая длительность недель совпадает с программой`() {
        // Секунды вписаны числами из таблицы плана, а не вычислены из WORKOUT_PRESETS.
        val expectedSec = listOf(
            19 * 60, 26 * 60, 23 * 60, 22 * 60, 22 * 60, 27 * 60,
            19 * 60 + 30, 23 * 60 + 30, 27 * 60, 31 * 60, 30 * 60,
        )

        assertEquals(
            expectedSec.map { it * 1000L },
            WORKOUT_PRESETS.map { it.settings.toWorkout().totalMs },
        )
    }

    @Test
    fun `точное совпадение возвращает неделю`() {
        val settings = WorkoutSettings(runMin = 8, runSec = 0, walkMin = 1, walkSec = 30, rounds = 3)

        assertEquals(6, presetWeekOf(settings))
        assertEquals(11, presetWeekOf(WorkoutSettings(30, 0, 0, 0, 1)))
    }

    @Test
    fun `значения по умолчанию дают неделю 2`() {
        assertEquals(2, presetWeekOf(WorkoutSettings()))
    }

    @Test
    fun `отличие в секундах или повторах даёт null`() {
        assertNull(presetWeekOf(WorkoutSettings(runMin = 2, runSec = 1, walkMin = 2, walkSec = 0, rounds = 7)))
        assertNull(presetWeekOf(WorkoutSettings(runMin = 2, runSec = 0, walkMin = 2, walkSec = 0, rounds = 6)))
        assertNull(presetWeekOf(WorkoutSettings(runMin = 30, runSec = 0, walkMin = 0, walkSec = 30, rounds = 1)))
    }
}
