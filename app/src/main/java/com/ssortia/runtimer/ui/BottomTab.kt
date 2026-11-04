package com.ssortia.runtimer.ui

import androidx.annotation.StringRes
import com.ssortia.runtimer.R

// Маршрут `settings` — форма тренировки (вкладка «Тренировка»), а `app_settings` — настройки
// приложения (вкладка «Настройки»). Каждому маршруту NavHost соответствует вкладка
internal const val ROUTE_SETTINGS = "settings"
internal const val ROUTE_APP_SETTINGS = "app_settings"
internal const val ROUTE_HISTORY = "history"
internal const val ROUTE_PROGRESS = "progress"

/** Вкладки нижней панели в порядке показа. Иконки сопоставляются вкладкам в Compose-коде. */
enum class BottomTab(val route: String, @StringRes val labelRes: Int) {
    WORKOUT(ROUTE_SETTINGS, R.string.tab_workout),
    HISTORY(ROUTE_HISTORY, R.string.tab_history),
    PROGRESS(ROUTE_PROGRESS, R.string.tab_progress),
    SETTINGS(ROUTE_APP_SETTINGS, R.string.tab_settings),
}

fun bottomTabOf(route: String?): BottomTab? = BottomTab.entries.firstOrNull { it.route == route }
