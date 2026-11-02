package com.ssortia.runtimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutSettingsTest {
    @Test
    fun `значения по умолчанию 2 2 7 верны`() {
        val settings = WorkoutSettings()

        assertEquals(WorkoutSettings(2, 0, 2, 0, 7), settings)
        assertNull(settings.validate())
    }

    @Test
    fun `пример 2 2 7 даёт 13 отрезков и 26 минут`() {
        val workout = WorkoutSettings().toWorkout()

        assertEquals(12, workout.at(1_559_999)?.index)
        assertNull(workout.at(1_560_000))
    }

    @Test
    fun `минуты и секунды складываются в длительности отрезков`() {
        val settings = WorkoutSettings(runMin = 1, runSec = 30, walkMin = 0, walkSec = 45, rounds = 3)

        assertEquals(Workout(runSec = 90, walkSec = 45, rounds = 3), settings.toWorkout())
    }

    @Test
    fun `нулевые минуты при ненулевых секундах допустимы`() {
        assertNull(WorkoutSettings(runMin = 0, runSec = 30, walkMin = 0, walkSec = 1).validate())
    }

    @Test
    fun `нулевые секунды при ненулевых минутах допустимы`() {
        assertNull(WorkoutSettings(runMin = 1, runSec = 0, walkMin = 1, walkSec = 0).validate())
    }

    @Test
    fun `нулевое время бега`() {
        assertEquals(SettingsError.RUN_ZERO, WorkoutSettings(runMin = 0, runSec = 0).validate())
    }

    @Test
    fun `нулевое время ходьбы`() {
        assertEquals(SettingsError.WALK_ZERO, WorkoutSettings(walkMin = 0, walkSec = 0).validate())
    }

    @Test
    fun `минуты верны до 99 включительно`() {
        assertNull(WorkoutSettings(runMin = 99).validate())
        assertNull(WorkoutSettings(walkMin = 99).validate())
        assertEquals(SettingsError.MINUTES_RANGE, WorkoutSettings(runMin = 100).validate())
        assertEquals(SettingsError.MINUTES_RANGE, WorkoutSettings(walkMin = 100).validate())
    }

    @Test
    fun `секунды верны до 59 включительно`() {
        assertNull(WorkoutSettings(runSec = 59).validate())
        assertNull(WorkoutSettings(walkSec = 59).validate())
        assertEquals(SettingsError.SECONDS_RANGE, WorkoutSettings(runSec = 60).validate())
        assertEquals(SettingsError.SECONDS_RANGE, WorkoutSettings(walkSec = 60).validate())
    }

    @Test
    fun `повторы верны от 1 до 99`() {
        assertNull(WorkoutSettings(rounds = 1).validate())
        assertNull(WorkoutSettings(rounds = 99).validate())
        assertEquals(SettingsError.ROUNDS_RANGE, WorkoutSettings(rounds = 0).validate())
        assertEquals(SettingsError.ROUNDS_RANGE, WorkoutSettings(rounds = 100).validate())
    }

    @Test
    fun `задержка старта по умолчанию равна 5 секундам`() {
        assertEquals(5, WorkoutSettings().startDelaySec)
    }

    @Test
    fun `задержка старта верна от 0 до 60`() {
        assertNull(WorkoutSettings(startDelaySec = 0).validate())
        assertNull(WorkoutSettings(startDelaySec = 60).validate())
        assertEquals(SettingsError.START_DELAY_RANGE, WorkoutSettings(startDelaySec = -1).validate())
        assertEquals(SettingsError.START_DELAY_RANGE, WorkoutSettings(startDelaySec = 61).validate())
    }

    @Test
    fun `задержка старта проверяется раньше нулевых отрезков`() {
        assertEquals(
            SettingsError.START_DELAY_RANGE,
            WorkoutSettings(runMin = 0, runSec = 0, startDelaySec = 61).validate(),
        )
        assertEquals(
            SettingsError.START_DELAY_RANGE,
            WorkoutSettings(walkMin = 0, walkSec = 0, startDelaySec = 61).validate(),
        )
    }

    @Test
    fun `задержка старта не входит в длительность тренировки`() {
        assertEquals(
            WorkoutSettings(startDelaySec = 0).toWorkout().totalMs,
            WorkoutSettings(startDelaySec = 60).toWorkout().totalMs,
        )
    }

    @Test
    fun `отрицательные значения отклоняются`() {
        assertEquals(SettingsError.MINUTES_RANGE, WorkoutSettings(runMin = -1, runSec = 30).validate())
        assertEquals(SettingsError.SECONDS_RANGE, WorkoutSettings(walkMin = 1, walkSec = -1).validate())
    }

    @Test
    fun `диапазоны проверяются раньше нулевых отрезков и в заданном порядке`() {
        assertEquals(SettingsError.MINUTES_RANGE, WorkoutSettings(runMin = -1, runSec = 60).validate())
        assertEquals(
            SettingsError.ROUNDS_RANGE,
            WorkoutSettings(runMin = 0, runSec = 0, rounds = 0).validate(),
        )
    }

    @Test
    fun `максимальные значения дают 197 отрезков и верную границу`() {
        val workout = WorkoutSettings(99, 59, 99, 59, 99).toWorkout()
        val totalMs = 11_998_000L * 99 - 5_999_000L

        // 99 повторов по 11 998 с без последней ходьбы: 197 отрезков с индексами 0..196
        assertEquals(196, workout.at(totalMs - 1)?.index)
        assertNull(workout.at(totalMs))
    }
}
