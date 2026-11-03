package com.ssortia.runtimer

import android.app.UiModeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun `системная тема следует за системой`() {
        assertTrue(isDarkTheme(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(isDarkTheme(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test
    fun `светлая тема светлая при любой системной`() {
        assertFalse(isDarkTheme(ThemeMode.LIGHT, systemDark = true))
        assertFalse(isDarkTheme(ThemeMode.LIGHT, systemDark = false))
    }

    @Test
    fun `тёмная тема тёмная при любой системной`() {
        assertTrue(isDarkTheme(ThemeMode.DARK, systemDark = true))
        assertTrue(isDarkTheme(ThemeMode.DARK, systemDark = false))
    }

    @Test
    fun `имя значения читается обратно`() {
        for (mode in ThemeMode.entries) assertEquals(mode, themeModeOf(mode.name))
    }

    @Test
    fun `отсутствующее и неизвестное имя дают системную тему`() {
        assertEquals(ThemeMode.SYSTEM, themeModeOf(null))
        assertEquals(ThemeMode.SYSTEM, themeModeOf(""))
        assertEquals(ThemeMode.SYSTEM, themeModeOf("dark"))
    }

    @Test
    fun `режим системы для каждого варианта`() {
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, ThemeMode.SYSTEM.toApplicationNightMode())
        assertEquals(UiModeManager.MODE_NIGHT_NO, ThemeMode.LIGHT.toApplicationNightMode())
        assertEquals(UiModeManager.MODE_NIGHT_YES, ThemeMode.DARK.toApplicationNightMode())
    }
}
