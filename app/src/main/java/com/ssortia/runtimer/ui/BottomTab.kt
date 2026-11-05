package com.ssortia.runtimer.ui

import androidx.annotation.StringRes
import com.ssortia.runtimer.R
import com.ssortia.runtimer.WorkoutState

// Маршрут `settings` — вкладка «Таймер»: всегда форма, экран тренировки лежит слоем поверх вкладок.
// `app_settings` — настройки приложения (вкладка «Настройки»). Каждому маршруту NavHost
// соответствует вкладка
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
 * Позиция центральной кнопки в нижней панели: она встаёт перед вкладкой с этим индексом, в середине
 * панели между «Историей» и «Прогрессом». Новая вкладка сдвинет её, это поймает `BottomTabTest`.
 */
fun startSlotIndex(): Int = BottomTab.entries.size / 2

fun bottomTabOf(route: String?): BottomTab? = BottomTab.entries.firstOrNull { it.route == route }

/** Режим центральной кнопки нижней панели: «Старт» вне тренировки, пауза во время неё. */
enum class CenterButtonMode { START, PAUSE, RESUME, PAUSE_DISABLED }

/** Пауза недоступна на отсчёте (часов ещё нет) и после финального сигнала, как у [pauseEnabled]. */
fun centerButtonMode(state: WorkoutState): CenterButtonMode = when (state) {
    WorkoutState.Idle -> CenterButtonMode.START
    is WorkoutState.Countdown -> CenterButtonMode.PAUSE_DISABLED
    is WorkoutState.Active -> when {
        !pauseEnabled(state) -> CenterButtonMode.PAUSE_DISABLED
        state.paused -> CenterButtonMode.RESUME
        else -> CenterButtonMode.PAUSE
    }
}

/** «Старт» доступен только при верных настройках формы, пауза и продолжение всегда. */
fun centerButtonEnabled(mode: CenterButtonMode, settingsValid: Boolean): Boolean = when (mode) {
    CenterButtonMode.START -> settingsValid
    CenterButtonMode.PAUSE, CenterButtonMode.RESUME -> true
    CenterButtonMode.PAUSE_DISABLED -> false
}

@StringRes
fun centerButtonLabelRes(mode: CenterButtonMode): Int = when (mode) {
    CenterButtonMode.START -> R.string.button_start
    CenterButtonMode.PAUSE, CenterButtonMode.PAUSE_DISABLED -> R.string.button_pause
    CenterButtonMode.RESUME -> R.string.button_resume
}
