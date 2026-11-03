package com.ssortia.runtimer

import android.app.UiModeManager

/** Тема оформления, выбранная на экране настроек. По умолчанию следует за системой. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

fun isDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Сохранённое имя значения. Неизвестное или отсутствующее читается как [ThemeMode.SYSTEM]. */
fun themeModeOf(name: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.SYSTEM

/**
 * Режим для `UiModeManager.setApplicationNightMode()` (API 31+). Константы встраиваются при
 * компиляции, поэтому функция проверяется обычным unit-тестом.
 */
fun ThemeMode.toApplicationNightMode(): Int = when (this) {
    ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
    ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
    ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
}
