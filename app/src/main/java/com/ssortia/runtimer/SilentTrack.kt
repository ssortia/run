package com.ssortia.runtimer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Непрерывно проигрывает тишину, пока идёт тренировка. Прошивки OPPO и realme через 5 минут
 * после блокировки экрана завершают процесс даже с сервисом переднего плана (`o-kill`
 * в `dumpsys activity exit-info`), но не трогают процесс, который сам выводит звук.
 *
 * Дорожка статическая и зациклена через [AudioTrack.setLoopPoints], поэтому отдельный поток
 * для записи не нужен. Аудиофокус не берётся: другие плееры дорожку не замечают.
 * Если дорожку не удалось создать или запустить, тренировка идёт без неё.
 */
class SilentTrack {
    private val track: AudioTrack? = createTrack()

    fun start() {
        val track = track ?: return
        try {
            track.play()
            Log.i(TAG, "Беззвучная дорожка запущена: usage=${ATTRIBUTES.usage}, playState=${track.playState}")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Беззвучная дорожка не запущена", e)
        }
    }

    // AudioTrack.release() безопасен при повторе, своего флага не нужно
    fun release() {
        val track = track ?: return
        track.release()
        Log.i(TAG, "Беззвучная дорожка остановлена")
    }

    private fun createTrack(): AudioTrack? {
        val samples = ShortArray(SAMPLE_RATE_HZ) // 1 с нулей
        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(ATTRIBUTES)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
                .build()
            // write() и setLoopPoints() о сбое сообщают кодом возврата, а не исключением
            val written = track.write(samples, 0, samples.size)
            if (written != samples.size) {
                Log.w(TAG, "Беззвучная дорожка не создана: записано $written из ${samples.size}")
            } else {
                // Без петли дорожка проиграла бы 1 с и замолчала
                val looped = track.setLoopPoints(0, samples.size, LOOP_FOREVER)
                if (looped == AudioTrack.SUCCESS) return track
                Log.w(TAG, "Беззвучная дорожка не создана: setLoopPoints вернул $looped")
            }
        } catch (e: RuntimeException) {
            // Builder бросает разные исключения, а без дорожки тренировка всё равно идёт
            Log.w(TAG, "Беззвучная дорожка не создана", e)
        }
        track?.release()
        return null
    }

    // Атрибуты в companion object по той же причине, что у Beeper: поле экземпляра
    // было бы ещё null при создании дорожки в инициализаторе track
    private companion object {
        const val TAG = "SilentTrack"
        const val LOOP_FOREVER = -1

        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
