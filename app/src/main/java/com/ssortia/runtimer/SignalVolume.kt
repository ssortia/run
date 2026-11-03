package com.ssortia.runtimer

import kotlin.math.roundToInt

// Громкость сигналов в процентах от системной громкости канала.
// Нуля нет намеренно: настройка не должна давать полную тишину.
const val MIN_VOLUME_PERCENT = 10
const val MAX_VOLUME_PERCENT = 100
const val DEFAULT_VOLUME_PERCENT = MAX_VOLUME_PERCENT
const val VOLUME_STEP_PERCENT = 5

fun clampVolume(percent: Int): Int = percent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)

/** Коэффициент для `AudioTrack.setVolume()`: регулировка только ослабляет звук. */
fun volumeGain(percent: Int): Float = percent / 100f

// Slider отдаёт Float: 34.999996 через toInt() дало бы 34 и разошлось с шагом
fun sliderToPercent(value: Float): Int = clampVolume(value.roundToInt())
