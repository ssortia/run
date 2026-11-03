package com.ssortia.runtimer

import kotlin.math.abs
import kotlin.math.roundToInt

// Задержка старта в секундах, настройка приложения: хранится отдельно от тренировки
const val DEFAULT_START_DELAY_SEC = 5

/** Допустимые значения задержки по возрастанию, ползунок ходит по их индексам. */
val START_DELAY_OPTIONS_SEC = listOf(0, 3, 5, 10, 15, 20, 30, 45, 60)

/** Число промежуточных положений `Slider`: крайние значения в `steps` не входят. */
val START_DELAY_SLIDER_STEPS = START_DELAY_OPTIONS_SEC.size - 2

/**
 * Приводит задержку к ближайшему допустимому значению, при равном расстоянии к меньшему.
 * Так читаются значения прежних версий (например, 7 или 25) и значения вне диапазона.
 */
fun snapStartDelay(sec: Int): Int = START_DELAY_OPTIONS_SEC.minBy { abs(it - sec) }

fun startDelayToSlider(sec: Int): Float = START_DELAY_OPTIONS_SEC.indexOf(snapStartDelay(sec)).toFloat()

// Slider отдаёт Float: 1.9999995 через toInt() дало бы 1 и разошлось с положением бегунка
fun sliderToStartDelay(value: Float): Int =
    START_DELAY_OPTIONS_SEC[value.roundToInt().coerceIn(START_DELAY_OPTIONS_SEC.indices)]
