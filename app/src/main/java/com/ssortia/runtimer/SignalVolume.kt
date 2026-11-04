package com.ssortia.runtimer

import kotlin.math.roundToInt

// Громкость сигналов в процентах от системной громкости канала. Ноль выключает звук сигналов,
// вибрация при этом остаётся, если она включена
const val MIN_VOLUME_PERCENT = 0
const val MAX_VOLUME_PERCENT = 100
const val DEFAULT_VOLUME_PERCENT = MAX_VOLUME_PERCENT
const val VOLUME_STEP_PERCENT = 10

/** Число промежуточных положений `Slider`: крайние значения в `steps` не входят. */
const val VOLUME_SLIDER_STEPS = (MAX_VOLUME_PERCENT - MIN_VOLUME_PERCENT) / VOLUME_STEP_PERCENT - 1

/**
 * Громкость, прижатая к диапазону и приведённая к ближайшему шагу ползунка, половина шага
 * округляется вверх. Значения прежних версий с шагом 5% (15, 35, 95) становятся 20, 40 и 100,
 * иначе ползунок показывал бы положение между делениями.
 */
fun snapVolume(percent: Int): Int {
    val clamped = percent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
    val steps = (clamped - MIN_VOLUME_PERCENT + VOLUME_STEP_PERCENT / 2) / VOLUME_STEP_PERCENT
    return MIN_VOLUME_PERCENT + steps * VOLUME_STEP_PERCENT
}

/** Коэффициент для `AudioTrack.setVolume()`: регулировка только ослабляет звук. */
fun volumeGain(percent: Int): Float = snapVolume(percent) / 100f

// Slider отдаёт Float: 34.999996 через toInt() дало бы 34 и разошлось с шагом
fun sliderToPercent(value: Float): Int = snapVolume(value.roundToInt())

/** Звук сигналов включён. При нуле сервис не создаёт `Beeper` и не берёт аудиофокус. */
fun isSoundOn(volumePercent: Int): Boolean = snapVolume(volumePercent) > 0

/**
 * Предупреждение «сигналы недоступны». При выключенном звуке `Beeper` нет намеренно, и
 * предупреждать не о чем.
 */
fun soundFailedWarning(soundOn: Boolean, beeperReady: Boolean): Boolean = soundOn && !beeperReady

/** Предупреждение о нулевой громкости медиа: при выключенном звуке сигналов оно не нужно. */
fun soundMutedWarning(soundOn: Boolean, mediaMuted: Boolean): Boolean = soundOn && mediaMuted

/**
 * Подсказка под ползунком громкости: при нуле без вибрации сигналов не будет совсем, с
 * вибрацией останется только она. При ненулевой громкости подсказки нет.
 */
fun volumeHint(volumePercent: Int, vibration: Boolean, vibrationOnly: String, noSignals: String): String? = when {
    isSoundOn(volumePercent) -> null
    vibration -> vibrationOnly
    else -> noSignals
}
