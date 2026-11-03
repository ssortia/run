package com.ssortia.runtimer

import android.content.res.Resources
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RawRes
import java.io.IOException

/**
 * Громкость медиа на нуле, и сигналов не будет слышно. Канал навигации на большинстве телефонов
 * следует громкости медиа: при смене атрибутов [Beeper] эту проверку надо пересмотреть.
 */
fun AudioManager.isMediaMuted(): Boolean = getStreamVolume(AudioManager.STREAM_MUSIC) == 0

/**
 * Проигрывает сигналы через канал навигации: они звучат поверх музыки, приглушают её на время
 * сигнала и следуют системной громкости, поэтому итоговая громкость равна [volumePercent],
 * умноженной на громкость канала.
 *
 * Аудиофокус берётся заранее через [duck] и держится до сигнала, [play] переводит его на таймер
 * возврата через [soundWithTailMs], окончательно его отпускает [release]. При выключенном
 * [duckMusic] (настройка «Приглушать музыку») фокус не берётся вовсе, и сигнал звучит поверх
 * музыки в полную громкость. Все методы вызываются только из главного потока.
 *
 * Готовятся только [signals]: чтение файлов из `res/raw` и создание дорожек идут
 * в конструкторе, в главном потоке, поэтому пробному сигналу незачем готовить все три.
 */
class Beeper(
    private val audioManager: AudioManager,
    private val resources: Resources,
    private val volumePercent: Int,
    private val duckMusic: Boolean,
    signals: Collection<Signal> = Signal.entries,
) {
    // ATTRIBUTES объявлены в companion object: tracks заполняется при создании объекта,
    // и поле экземпляра ниже него было бы ещё null
    private val tracks: Map<Signal, AudioTrack?> = run {
        // START и INTERVAL звучат одинаково: файл разбирается один раз, а дорожки у них свои,
        // потому что у каждой свои stop() и reloadStaticData()
        val sounds = signals.map(::soundOf).distinct().associateWith(::readSound)
        signals.associateWith { createTrack(it, sounds.getValue(soundOf(it))) }
    }

    // Без слушателя: сигнал длится доли секунды, реагировать на смену фокуса незачем
    private val focusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(ATTRIBUTES)
            .build()
    private val handler = Handler(Looper.getMainLooper())
    private val abandonFocus = Runnable { audioManager.abandonAudioFocusRequest(focusRequest) }
    private var released = false

    // Фокус взят через duck() и держится без таймера до сигнала или stopDucking()
    private var holding = false

    /** false, если хотя бы один из сигналов не удалось подготовить: о такой тренировке надо предупредить. */
    val isReady: Boolean get() = tracks.values.all { it != null }

    /**
     * Проигрывает сигнал с начала. Потерянный сигнал не должен останавливать тренировку.
     * Если сигнал проиграть не удалось, фокус, взятый через [duck], возвращается сразу:
     * таймера возврата в этом случае нет, и музыка осталась бы приглушённой.
     */
    fun play(signal: Signal) {
        if (released) return
        val track = tracks[signal]
        if (track == null) {
            stopDucking()
            return
        }
        try {
            // Без stop() и reloadStaticData() второй запуск будет беззвучным
            track.stop()
            val reloaded = track.reloadStaticData()
            if (reloaded == AudioTrack.SUCCESS) {
                if (duckMusic) requestFocus(signal)
                track.play()
            } else {
                Log.w(TAG, "Сигнал $signal не проигран: reloadStaticData вернул $reloaded")
                stopDucking()
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Не удалось проиграть сигнал $signal", e)
            // После requestFocus() фокус вернёт таймер, тогда stopDucking() ничего не делает
            stopDucking()
        }
    }

    /**
     * Заранее приглушает музыку перед сигналом: система снижает громкость плавно. Фокус держится
     * до [play] или [stopDucking]. Повторный вызов ничего не меняет. Таймер возврата после
     * прошлого сигнала снимается: на коротких отрезках музыка остаётся приглушённой.
     *
     * Без готовых сигналов и при выключенном [duckMusic] музыку приглушать незачем. При отказе
     * в фокусе [holding] не ставится, и следующий вызов запросит фокус снова.
     */
    fun duck() {
        if (released || holding || !duckMusic || !isReady) return
        handler.removeCallbacks(abandonFocus)
        holding = requestFocusLogged("заранее")
    }

    /** Возвращает громкость музыки, если она приглушена через [duck], а сигнал ещё не прозвучал. */
    fun stopDucking() {
        if (!holding) return
        holding = false
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    /** Сразу прекращает звук. Вызывать можно повторно, после этого [play] ничего не проигрывает. */
    fun release() {
        released = true
        holding = false
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
        // Если фокус уже взят через duck(), повторный запрос того же фокуса ничего не меняет
        requestFocusLogged("для сигнала $signal")
        holding = false
        handler.postDelayed(abandonFocus, soundWithTailMs(signal))
    }

    private fun requestFocusLogged(purpose: String): Boolean {
        val result = audioManager.requestAudioFocus(focusRequest)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return true
        Log.w(TAG, "Фокус $purpose не получен: $result")
        return false
    }

    private fun createTrack(signal: Signal, pcm: ShortArray?): AudioTrack? {
        if (pcm == null) return null
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

    // Старт звучит так же, как смена отрезка
    @RawRes
    private fun soundOf(signal: Signal): Int = when (signal) {
        Signal.START, Signal.INTERVAL -> R.raw.signal_interval
        Signal.FINISH -> R.raw.signal_finish
    }

    private fun readSound(@RawRes id: Int): ShortArray? {
        val name = resources.getResourceEntryName(id)
        val bytes = try {
            resources.openRawResource(id).use { it.readBytes() }
        } catch (e: IOException) {
            Log.w(TAG, "Не удалось прочитать звук $name", e)
            return null
        }
        return decodeWav(bytes).also {
            if (it == null) Log.w(TAG, "Звук $name не в формате PCM 16 бит моно $SAMPLE_RATE_HZ Гц")
        }
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
