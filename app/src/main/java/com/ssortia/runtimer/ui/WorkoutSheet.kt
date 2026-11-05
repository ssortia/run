package com.ssortia.runtimer.ui

import com.ssortia.runtimer.WorkoutState

// Чистые правила слоя экрана тренировки поверх вкладок: когда он во весь экран, когда
// разворачивается сам, куда едет при отпускании и какие события прокрутки забирает себе.
// Смещение слоя в пикселях отсчитывается вниз: 0 — развёрнут, высота области — свёрнут

/**
 * Экран тренировки во весь экран: идёт отсчёт или тренировка и слой тренировки развёрнут. Единственное
 * место, где сочетаются состояние и развёрнутость: по нему составляется слой и выбираются системные
 * панели, а значки строки состояния — по нему же, но с развёрнутостью из [sheetCoversStatusBar].
 */
fun isWorkoutFullscreen(state: WorkoutState, expanded: Boolean): Boolean =
    state !is WorkoutState.Idle && expanded

/**
 * Закрывает ли слой строку состояния: он развёрнут и опущен меньше чем на её высоту [statusBarPx].
 * Пока слой выезжает, его тянут пальцем или он сворачивается, сверху видна вкладка, и значки
 * строки состояния должны следовать за ней. Без строки состояния (высота 0) слой закрывает её
 * только в самом верху.
 */
fun sheetCoversStatusBar(expanded: Boolean, offsetPx: Float, statusBarPx: Float): Boolean =
    expanded && offsetPx < statusBarPx.coerceAtLeast(1f)

/**
 * Развернуть экран тренировки при старте: только на переходе из Idle. Прошлое значение переживает
 * поворот, иначе поворот во время тренировки снова разворачивал бы свёрнутый экран. Без перехода
 * (activity открыта уже во время тренировки) развёрнутость берётся из начального значения.
 */
fun shouldExpandWorkout(wasIdle: Boolean, idle: Boolean): Boolean = wasIdle && !idle

/** Доля высоты области, на которую надо опустить экран тренировки, чтобы при отпускании он свернулся. */
internal const val SHEET_COLLAPSE_FRACTION = 0.25f

/**
 * Свернуть ли экран тренировки, когда палец отпустил его на смещении [offsetPx] вниз из [heightPx].
 * Флинг важнее смещения: быстрый взмах вниз (скорость не меньше [flingVelocityPx]) сворачивает и
 * от самого верха, быстрый взмах вверх возвращает и почти свёрнутый экран. Без флинга экран
 * сворачивается, если опущен не меньше чем на [SHEET_COLLAPSE_FRACTION] высоты. Пока высота
 * области неизвестна ([heightPx] не больше 0), экран не сворачивается.
 */
fun shouldCollapseSheet(
    offsetPx: Float,
    heightPx: Float,
    velocityPx: Float,
    flingVelocityPx: Float,
): Boolean = when {
    heightPx <= 0f -> false
    velocityPx >= flingVelocityPx -> true
    velocityPx <= -flingVelocityPx -> false
    else -> offsetPx >= heightPx * SHEET_COLLAPSE_FRACTION
}

/**
 * Смещение слоя после сдвига пальцем на [deltaPx]: в пределах от 0 до [heightPx]. Поглощённая
 * часть жеста — разница нового и прежнего смещения. Пока высота неизвестна (не больше 0), слой
 * стоит наверху.
 */
fun sheetDragTo(offsetPx: Float, deltaPx: Float, heightPx: Float): Float =
    (offsetPx + deltaPx).coerceIn(0f, heightPx.coerceAtLeast(0f))

/** Прокрутка вверх сначала поднимает опущенный слой и только потом прокручивает колонку. */
fun sheetTakesPreScroll(deltaPx: Float, offsetPx: Float): Boolean = deltaPx < 0f && offsetPx > 0f

/**
 * Остаток прокрутки вниз (колонка прокручена до верха) опускает слой, но только от пальца: инерция
 * колонки слой не тянет.
 */
fun sheetTakesPostScroll(deltaPx: Float, fromUser: Boolean): Boolean = deltaPx > 0f && fromUser

/** Опущенный слой решает судьбу отпускания сам, и колонка инерцию не получает. */
fun sheetTakesFling(offsetPx: Float): Boolean = offsetPx > 0f
