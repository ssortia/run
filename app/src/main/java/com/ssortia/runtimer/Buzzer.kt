package com.ssortia.runtimer

import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

/**
 * Дублирует сигналы вибрацией по паттернам [vibrationFor]. Атрибуты будильника: с другими система
 * может глушить вибрацию при выключенном экране или в беззвучном режиме.
 *
 * Без вибромотора [play] ничего не делает. [release] сразу прекращает вибрацию.
 */
class Buzzer(private val vibrator: Vibrator?) {
    private var released = false

    /** Потерянная вибрация не должна останавливать тренировку. */
    fun play(signal: Signal) {
        if (released || vibrator?.hasVibrator() != true) return
        try {
            val effect = VibrationEffect.createWaveform(vibrationFor(signal), NO_REPEAT)
            // Перегрузка с AudioAttributes устарела на API 33, но работает, а замена есть только с него
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, ATTRIBUTES)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Не удалось подать вибрацию $signal", e)
        }
    }

    /** Вызывать можно повторно, после этого [play] ничего не делает. */
    fun release() {
        released = true
        vibrator?.cancel()
    }

    private companion object {
        const val TAG = "Buzzer"
        const val NO_REPEAT = -1

        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
