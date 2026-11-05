package com.ssortia.runtimer.ui

import androidx.annotation.StringRes
import com.ssortia.runtimer.R
import com.ssortia.runtimer.WorkoutState

// Маршрут `settings` — вкладка «Таймер»: форма вне тренировки, экран отсчёта или тренировки во время
// неё. `app_settings` — настройки приложения (вкладка «Настройки»). Каждому маршруту NavHost
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

/**
 * Экран тренировки во весь экран: идёт отсчёт или тренировка и открыт «Таймер». Без маршрута
 * (стек ещё не готов) открыт стартовый маршрут, то есть «Таймер».
 */
fun isWorkoutFullscreen(state: WorkoutState, route: String?): Boolean =
    state !is WorkoutState.Idle && (route == null || route == ROUTE_SETTINGS)

/**
 * Открыть «Таймер» при старте тренировки: только на переходе из Idle. Прошлое значение переживает
 * поворот, иначе поворот во время тренировки возвращал бы с другой вкладки на «Таймер». Без перехода
 * (activity открыта уже во время тренировки) вкладка остаётся стартовой.
 */
fun shouldOpenTimer(wasIdle: Boolean, idle: Boolean): Boolean = wasIdle && !idle

/**
 * Куда «Свернуть» ведёт сразу после старта: на вкладку, где нажали «Старт», если это не «Таймер»,
 * иначе на «Историю». Вкладки, открытые до старта, не учитываются.
 */
fun collapseRouteOnStart(startRoute: String?): String =
    startRoute?.takeIf { bottomTabOf(it) != null && it != ROUTE_SETTINGS } ?: ROUTE_HISTORY

/** Куда «Свернуть» ведёт после перехода на [route]: на последнюю открытую вкладку, кроме «Таймера». */
fun nextCollapseRoute(collapseRoute: String, route: String?): String =
    route?.takeIf { bottomTabOf(it) != null && it != ROUTE_SETTINGS } ?: collapseRoute

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
