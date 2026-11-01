package com.ssortia.runtimer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/** Проигрывает сигналы через канал будильника, поэтому они слышны при выключенном звуке медиа. */
class Beeper {
    private val tracks: Map<Signal, AudioTrack?> = Signal.entries.associateWith(::createTrack)

    /** false, если хотя бы один сигнал не удалось подготовить: о такой тренировке надо предупредить. */
    val isReady: Boolean get() = tracks.values.all { it != null }

    /** Проигрывает сигнал с начала. Потерянный сигнал не должен останавливать тренировку. */
    fun play(signal: Signal) {
        val track = tracks[signal] ?: return
        try {
            // Без stop() и reloadStaticData() второй запуск будет беззвучным
            track.stop()
            val reloaded = track.reloadStaticData()
            if (reloaded == AudioTrack.SUCCESS) {
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
        tracks.values.forEach { it?.release() }
    }

    private fun createTrack(signal: Signal): AudioTrack? {
        val pcm = synthesize(tonesFor(signal))
        var track: AudioTrack? = null
        try {
            track = buildTrack(pcm)
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
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
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
    }
}
