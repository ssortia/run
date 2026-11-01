package com.ssortia.runtimer.ui

import com.ssortia.runtimer.SettingsError
import com.ssortia.runtimer.WorkoutSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsInputTest {
    private fun input(
        runMin: String = "2",
        runSec: String = "0",
        walkMin: String = "2",
        walkSec: String = "0",
        rounds: String = "7",
    ) = SettingsInput(runMin, runSec, walkMin, walkSec, rounds)

    @Test
    fun `пустое поле считается нулём`() {
        assertEquals(0, parseField(""))
    }

    @Test
    fun `обычное число`() {
        assertEquals(42, parseField("42"))
    }

    @Test
    fun `ведущие нули не мешают`() {
        assertEquals(7, parseField("007"))
    }

    @Test
    fun `не число считается нулём`() {
        assertEquals(0, parseField("abc"))
    }

    @Test
    fun `в поле остаются только цифры`() {
        assertEquals("12", digitsOnly("-1 a2."))
        assertEquals("", digitsOnly("abc"))
    }

    @Test
    fun `цифры других алфавитов отбрасываются`() {
        assertEquals("", digitsOnly("٣"))
    }

    @Test
    fun `пустые минуты и 30 секунд дают верные настройки`() {
        val settings = input(runMin = "", runSec = "30").toSettings()

        assertNull(settings.validate())
        assertEquals(30, settings.toWorkout().runSec)
    }

    @Test
    fun `значение вне диапазона даёт ошибку validate`() {
        assertEquals(SettingsError.MINUTES_RANGE, input(runMin = "100").toSettings().validate())
        assertEquals(SettingsError.SECONDS_RANGE, input(walkSec = "60").toSettings().validate())
        assertEquals(SettingsError.ROUNDS_RANGE, input(rounds = "100").toSettings().validate())
    }

    @Test
    fun `число больше Int даёт ошибку диапазона, а не ноль`() {
        val huge = "99999999999"

        assertEquals(SettingsError.MINUTES_RANGE, input(runMin = huge).toSettings().validate())
        assertEquals(SettingsError.SECONDS_RANGE, input(runSec = huge).toSettings().validate())
        assertEquals(SettingsError.ROUNDS_RANGE, input(rounds = huge).toSettings().validate())
    }

    @Test
    fun `пустые поля дают понятные ошибки`() {
        assertEquals(
            SettingsError.RUN_ZERO,
            input(runMin = "", runSec = "").toSettings().validate(),
        )
        assertEquals(
            SettingsError.WALK_ZERO,
            input(walkMin = "", walkSec = "").toSettings().validate(),
        )
        assertEquals(SettingsError.ROUNDS_RANGE, input(rounds = "").toSettings().validate())
    }

    @Test
    fun `начальные значения проходят через форму без изменений`() {
        val settings = WorkoutSettings(runMin = 3, runSec = 15, walkMin = 1, walkSec = 45, rounds = 9)

        assertEquals(settings, SettingsInput.from(settings).toSettings())
    }

    @Test
    fun `у каждой ошибки своя строка`() {
        val messages = SettingsError.entries.map { it.messageRes() }

        assertTrue(messages.all { it != 0 })
        assertEquals(SettingsError.entries.size, messages.toSet().size)
    }
}
