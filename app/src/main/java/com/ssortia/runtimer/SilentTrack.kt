package com.ssortia.runtimer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Непрерывно проигрывает тишину, пока идёт тренировка. Прошивки Oplus (realme, OPPO, OnePlus)
 * через 5 минут после блокировки экрана завершают процесс даже с сервисом переднего плана
 * (`o-kill` в `dumpsys activity exit-info`), но не трогают процесс, который сам выводит звук.
 *
 * Дорожка статическая и зациклена через [AudioTrack.setLoopPoints], поэтому отдельный поток
 * для записи не нужен. Аудиофокус не берётся, поэтому другие плееры не встают на паузу.
 * Если дорожку не удалось создать или запустить, тренировка идёт без неё.
 *
 * Запуск и остановка намеренно пишутся в журнал уровнем info: это единственный след дорожки
 * без звука. Запись о запуске значит лишь, что `play()` вызван, а играет ли дорожка,
 * показывает `adb shell dumpsys audio` (playback configurations).
 */
class SilentTrack {
    private val track: AudioTrack? = createTrack()
    private var started = false

    fun start() {
        val track = track ?: return
        try {
            track.play()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Беззвучная дорожка не запущена", e)
            return
        }
        started = true
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            Log.i(TAG, "Беззвучная дорожка запущена")
        } else {
            Log.w(TAG, "Беззвучная дорожка не запущена: playState=${track.playState}")
        }
    }

    /** Безопасен при повторе: [AudioTrack.release] сам это допускает. */
    fun release() {
        val track = track ?: return
        track.release()
        if (started) Log.i(TAG, "Беззвучная дорожка остановлена")
        started = false
    }

    private fun createTrack(): AudioTrack? {
        val samples = ShortArray(LOOP_SAMPLES)
        val track = try {
            AudioTrack.Builder()
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
        } catch (e: RuntimeException) {
            // Builder бросает разные исключения, а без дорожки тренировка всё равно идёт
            Log.w(TAG, "Беззвучная дорожка не создана", e)
            return null
        }
        // write() и setLoopPoints() о сбое сообщают кодом возврата, а не исключением
        val written = track.write(samples, 0, samples.size)
        if (written != samples.size) {
            Log.w(TAG, "Беззвучная дорожка не создана: записано $written из ${samples.size}")
            track.release()
            return null
        }
        // Без петли дорожка проиграла бы буфер один раз и замолчала
        val looped = track.setLoopPoints(0, samples.size, LOOP_FOREVER)
        if (looped != AudioTrack.SUCCESS) {
            Log.w(TAG, "Беззвучная дорожка не создана: setLoopPoints вернул $looped")
            track.release()
            return null
        }
        return track
    }

    // Атрибуты в companion object, как у Beeper: поле экземпляра, объявленное ниже track,
    // было бы ещё null, когда инициализатор track вызывает createTrack()
    private companion object {
        const val TAG = "SilentTrack"
        const val LOOP_FOREVER = -1
        // 1 с нулей: длина петли не важна, лишь бы буфер был небольшим
        const val LOOP_SAMPLES = SAMPLE_RATE_HZ

        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
