package com.ssortia.runtimer.ui

import androidx.compose.runtime.saveable.SaverScope
import com.ssortia.runtimer.R
import com.ssortia.runtimer.SettingsError
import com.ssortia.runtimer.WORKOUT_PRESETS
import com.ssortia.runtimer.WorkoutSettings
import com.ssortia.runtimer.presetWeekOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFormTest {
    @Test
    fun `каждая ошибка сопоставлена своей строке`() {
        assertEquals(R.string.error_run_zero, SettingsError.RUN_ZERO.messageRes())
        assertEquals(R.string.error_walk_zero, SettingsError.WALK_ZERO.messageRes())
        assertEquals(R.string.error_rounds_range, SettingsError.ROUNDS_RANGE.messageRes())
        assertEquals(R.string.error_seconds_range, SettingsError.SECONDS_RANGE.messageRes())
        assertEquals(R.string.error_minutes_range, SettingsError.MINUTES_RANGE.messageRes())
    }

    @Test
    fun `пределы в сообщениях совпадают с проверкой`() {
        assertEquals(listOf<Any>(), SettingsError.RUN_ZERO.messageArgs().toList())
        assertEquals(listOf<Any>(), SettingsError.WALK_ZERO.messageArgs().toList())
        assertEquals(listOf<Any>(99), SettingsError.ROUNDS_RANGE.messageArgs().toList())
        assertEquals(listOf<Any>(59), SettingsError.SECONDS_RANGE.messageArgs().toList())
        assertEquals(listOf<Any>(99), SettingsError.MINUTES_RANGE.messageArgs().toList())
    }

    @Test
    fun `форма восстанавливается после поворота без перестановки полей`() {
        val original = WorkoutSettings(runMin = 1, runSec = 2, walkMin = 3, walkSec = 4, rounds = 5)

        val saved = with(WorkoutSettingsSaver) { SaverScope { true }.save(original) }

        assertEquals(original, WorkoutSettingsSaver.restore(saved!!))
    }

    @Test
    fun `неверное сохранённое значение восстанавливается как есть`() {
        val original = WorkoutSettings(runMin = 120, runSec = 75, walkMin = 0, walkSec = 0, rounds = 150)

        val saved = with(WorkoutSettingsSaver) { SaverScope { true }.save(original) }

        assertEquals(original, WorkoutSettingsSaver.restore(saved!!))
    }

    @Test
    fun `шаг повторов прибавляет и вычитает`() {
        assertEquals(8, stepRounds(7, 1))
        assertEquals(6, stepRounds(7, -1))
        assertTrue(canDecreaseRounds(7))
        assertTrue(canIncreaseRounds(7))
    }

    @Test
    fun `ноль повторов считается одним повтором`() {
        assertEquals(2, stepRounds(0, 1))
        assertEquals(1, stepRounds(0, -1))
        assertFalse(canDecreaseRounds(0))
        assertTrue(canIncreaseRounds(0))
    }

    @Test
    fun `меньше одного повтора не уходит`() {
        assertEquals(1, stepRounds(1, -1))
        assertFalse(canDecreaseRounds(1))
        assertTrue(canIncreaseRounds(1))
    }

    @Test
    fun `больше максимума повторов не уходит`() {
        assertEquals(99, stepRounds(99, 1))
        assertFalse(canIncreaseRounds(99))
        assertTrue(canDecreaseRounds(99))
    }

    @Test
    fun `значение вне диапазона сначала прижимается к максимуму`() {
        assertEquals(98, stepRounds(150, -1))
        assertFalse(canIncreaseRounds(150))
    }

    @Test
    fun `большое значение прижимается без переполнения`() {
        assertEquals(98, stepRounds(Int.MAX_VALUE, -1))
        assertEquals(99, stepRounds(Int.MAX_VALUE, 1))
        assertEquals(1, stepRounds(Int.MIN_VALUE, -1))
    }

    @Test
    fun `неделя 10 без шага показывает шаг 0 00 и проходит проверку`() {
        val week10 = WORKOUT_PRESETS.first { it.week == 10 }.settings
        assertEquals("0:00", segmentTimeText(week10.walkMin, week10.walkSec))
        assertNull(week10.validate())
        assertEquals(10, presetWeekOf(week10))
    }

    @Test
    fun `время отрезка не прижимается к диапазону`() {
        assertEquals("120:75", segmentTimeText(120, 75))
    }
}
