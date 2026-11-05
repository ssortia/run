package com.ssortia.runtimer.ui

import androidx.annotation.StringRes
import com.ssortia.runtimer.R
import com.ssortia.runtimer.WorkoutState

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

/**
 * Позиция «Старта» в нижней панели: он встаёт перед вкладкой с этим индексом, в середине панели
 * между «Историей» и «Прогрессом». Новая вкладка сдвинет его, это поймает `BottomTabTest`.
 */
fun startSlotIndex(): Int = BottomTab.entries.size / 2

fun bottomTabOf(route: String?): BottomTab? = BottomTab.entries.firstOrNull { it.route == route }

/**
 * Экран тренировки во весь экран: идёт отсчёт или тренировка и открыт «Таймер». Без маршрута
 * (стек ещё не готов) открыт стартовый маршрут, то есть «Таймер».
 */
fun isWorkoutFullscreen(state: WorkoutState, route: String?): Boolean =
    state !is WorkoutState.Idle && (route == null || route == ROUTE_SETTINGS)
