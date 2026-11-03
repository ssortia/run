package com.ssortia.runtimer.ui

import androidx.compose.runtime.saveable.SaverScope
import com.ssortia.runtimer.R
import com.ssortia.runtimer.SettingsError
import com.ssortia.runtimer.WorkoutSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(SettingsError.MINUTES_RANGE, input(walkMin = huge).toSettings().validate())
        assertEquals(SettingsError.SECONDS_RANGE, input(walkSec = huge).toSettings().validate())
    }

    @Test
    fun `граница Int разбирается без переполнения`() {
        assertEquals(Int.MAX_VALUE, parseField("2147483647"))
        assertEquals(Int.MAX_VALUE, parseField("2147483648"))
    }

    @Test
    fun `отрицательное число не превращается в положительное`() {
        assertEquals(-1, parseField("-1"))
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
        val settings = WorkoutSettings(
            runMin = 3,
            runSec = 15,
            walkMin = 1,
            walkSec = 45,
            rounds = 9,
        )

        assertEquals(settings, SettingsInput.from(settings).toSettings())
    }

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
        val original = SettingsInput("1", "2", "3", "", "5")

        val saved = with(SettingsInputSaver) { SaverScope { true }.save(original) }

        assertEquals(original, SettingsInputSaver.restore(saved!!))
    }
}
