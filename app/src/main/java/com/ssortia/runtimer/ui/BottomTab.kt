package com.ssortia.runtimer.ui

import androidx.annotation.StringRes
import com.ssortia.runtimer.R

internal const val ROUTE_SETTINGS = "settings"
internal const val ROUTE_APP_SETTINGS = "app_settings"
internal const val ROUTE_HISTORY = "history"
internal const val ROUTE_PROGRESS = "progress"

/** Вкладки нижней панели в порядке показа. Иконки сопоставляются вкладкам в Compose-коде. */
enum class BottomTab(val route: String, @StringRes val labelRes: Int) {
    WORKOUT(ROUTE_SETTINGS, R.string.tab_workout),
    HISTORY(ROUTE_HISTORY, R.string.button_history),
    PROGRESS(ROUTE_PROGRESS, R.string.button_progress),
    SETTINGS(ROUTE_APP_SETTINGS, R.string.button_settings),
}

fun bottomTabOf(route: String?): BottomTab? = BottomTab.entries.firstOrNull { it.route == route }
