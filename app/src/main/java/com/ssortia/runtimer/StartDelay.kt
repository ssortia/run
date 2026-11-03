package com.ssortia.runtimer

import kotlin.math.roundToInt

// Задержка старта в секундах, настройка приложения: хранится отдельно от тренировки
const val MIN_START_DELAY_SEC = 0
const val MAX_START_DELAY_SEC = 60
const val DEFAULT_START_DELAY_SEC = 5
const val START_DELAY_STEP_SEC = 5

/** Число промежуточных положений `Slider`: крайние значения в `steps` не входят. */
const val START_DELAY_SLIDER_STEPS = (MAX_START_DELAY_SEC - MIN_START_DELAY_SEC) / START_DELAY_STEP_SEC - 1

fun clampStartDelay(sec: Int): Int = sec.coerceIn(MIN_START_DELAY_SEC, MAX_START_DELAY_SEC)

// Slider отдаёт Float: 4.9999995 через toInt() дало бы 4 и разошлось с шагом
fun sliderToStartDelay(value: Float): Int = clampStartDelay(value.roundToInt())
