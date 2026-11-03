package com.ssortia.runtimer

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Проигрывает сигналы через канал навигации: они звучат поверх музыки, приглушают её на время
 * сигнала и следуют системной громкости, поэтому итоговая громкость равна [volumePercent],
 * умноженной на громкость канала.
 *
 * Аудиофокус берётся на каждый сигнал и возвращается по таймеру через [focusHoldMs],
 * окончательно его отпускает [release]. [play] и [release] вызываются только из главного потока.
 */
class Beeper(private val audioManager: AudioManager, private val volumePercent: Int) {
    // ATTRIBUTES объявлены в companion object: tracks заполняется при создании объекта,
    // и поле экземпляра ниже него было бы ещё null
    private val tracks: Map<Signal, AudioTrack?> = Signal.entries.associateWith(::createTrack)

    // Без слушателя: сигнал длится доли секунды, реагировать на смену фокуса незачем
    private val focusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(ATTRIBUTES)
            .build()
    private val handler = Handler(Looper.getMainLooper())
    private val abandonFocus = Runnable { audioManager.abandonAudioFocusRequest(focusRequest) }
    private var released = false

    /** false, если хотя бы один сигнал не удалось подготовить: о такой тренировке надо предупредить. */
    val isReady: Boolean get() = tracks.values.all { it != null }

    /** Проигрывает сигнал с начала. Потерянный сигнал не должен останавливать тренировку. */
    fun play(signal: Signal) {
        if (released) return
        val track = tracks[signal] ?: return
        try {
            // Без stop() и reloadStaticData() второй запуск будет беззвучным
            track.stop()
            val reloaded = track.reloadStaticData()
            if (reloaded == AudioTrack.SUCCESS) {
                requestFocus(signal)
                track.play()
            } else {
                Log.w(TAG, "Сигнал $signal не проигран: reloadStaticData вернул $reloaded")
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Не удалось проиграть сигнал $signal", e)
        }
    }

    /** Сразу прекращает звук. Вызывать можно повторно, после этого [play] ничего не проигрывает. */
    fun release() {
        released = true
        handler.removeCallbacks(abandonFocus)
        // Музыка не должна оставаться приглушённой после «Стоп»
        audioManager.abandonAudioFocusRequest(focusRequest)
        tracks.values.forEach { it?.release() }
    }

    /**
     * Отказ в фокусе не отменяет сигнал: потерянный сигнал опаснее неприглушённой музыки.
     * Прежний таймер снимается, иначе запоздавший возврат отпустил бы фокус посреди сигнала.
     */
    private fun requestFocus(signal: Signal) {
        handler.removeCallbacks(abandonFocus)
        val result = audioManager.requestAudioFocus(focusRequest)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "Фокус для сигнала $signal не получен: $result")
        }
        handler.postDelayed(abandonFocus, focusHoldMs(signal))
    }

    private fun createTrack(signal: Signal): AudioTrack? {
        val pcm = synthesize(tonesFor(signal))
        var track: AudioTrack? = null
        try {
            track = buildTrack(pcm)
            val volumeResult = track.setVolume(volumeGain(volumePercent))
            if (volumeResult != AudioTrack.SUCCESS) {
                Log.w(TAG, "Громкость сигнала $signal не задана: setVolume вернул $volumeResult")
            }
            val written = track.write(pcm, 0, pcm.size)
            if (written == pcm.size) return track
            Log.w(TAG, "Сигнал $signal не подготовлен: записано $written из ${pcm.size}")
        } catch (e: RuntimeException) {
            // Builder бросает разные исключения, а без одного сигнала тренировка всё равно идёт
            Log.w(TAG, "Не удалось подготовить сигнал $signal", e)
        }
        track?.release()
        return null
    }

    private fun buildTrack(pcm: ShortArray): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(ATTRIBUTES)
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE_HZ)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
        .build()

    private companion object {
        const val TAG = "Beeper"

        // Один экземпляр на дорожки и запрос фокуса, чтобы канал у них не разошёлся
        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
