package com.ssortia.runtimer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/** Проигрывает сигналы через канал будильника, поэтому они слышны при выключенном звуке медиа. */
class Beeper {
    private class Sound(val track: AudioTrack, val durationMs: Long)

    private val sounds: Map<Signal, Sound?> = Signal.entries.associateWith(::createSound)

    /**
     * Проигрывает сигнал с начала и возвращает его длительность в миллисекундах.
     * Если сигнал проиграть не удалось, возвращает 0: ждать окончания звука незачем.
     */
    fun play(signal: Signal): Long {
        val sound = sounds[signal] ?: return 0
        return try {
            // Без stop() и reloadStaticData() второй запуск будет беззвучным
            sound.track.stop()
            val reloaded = sound.track.reloadStaticData()
            check(reloaded == AudioTrack.SUCCESS) { "reloadStaticData: $reloaded" }
            sound.track.play()
            sound.durationMs
        } catch (e: IllegalStateException) {
            // Потерянный сигнал не должен останавливать тренировку
            Log.w(TAG, "Не удалось проиграть сигнал $signal", e)
            0
        }
    }

    /** Сразу прекращает звук. Вызывать можно повторно, после этого [play] ничего не проигрывает. */
    fun release() {
        sounds.values.forEach { it?.track?.release() }
    }

    private fun createSound(signal: Signal): Sound? {
        val pcm = synthesize(tonesFor(signal))
        return try {
            val track = buildTrack(pcm)
            val written = track.write(pcm, 0, pcm.size)
            if (written != pcm.size) {
                track.release()
                error("write: $written из ${pcm.size}")
            }
            Sound(track, durationMs(pcm))
        } catch (e: RuntimeException) {
            // Builder бросает разные исключения, а без одного сигнала тренировка всё равно идёт
            Log.w(TAG, "Не удалось подготовить сигнал $signal", e)
            null
        }
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
