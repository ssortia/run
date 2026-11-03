package com.ssortia.runtimer

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Достаёт сэмплы из WAV. Принимается только PCM 16 бит, моно, [SAMPLE_RATE_HZ]: под этот формат
 * настроен `AudioTrack` в `Beeper`, поэтому любой другой файл даёт null. Чанки, кроме `fmt `
 * и `data`, пропускаются.
 */
fun decodeWav(bytes: ByteArray): ShortArray? {
    if (bytes.size < HEADER_SIZE || ascii(bytes, 0) != "RIFF" || ascii(bytes, 8) != "WAVE") return null
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var formatOk = false
    var pos = HEADER_SIZE
    while (pos + CHUNK_HEADER_SIZE <= bytes.size) {
        val size = buffer.getInt(pos + 4)
        val body = pos + CHUNK_HEADER_SIZE
        if (size < 0 || size > bytes.size - body) return null
        when (ascii(bytes, pos)) {
            "fmt " -> {
                if (size < FMT_SIZE) return null
                formatOk = buffer.getShort(body).toInt() == FORMAT_PCM &&
                    buffer.getShort(body + 2).toInt() == 1 &&
                    buffer.getInt(body + 4) == SAMPLE_RATE_HZ &&
                    buffer.getShort(body + 14).toInt() == Short.SIZE_BITS
            }
            "data" -> {
                // Пустые данные дали бы AudioTrack с буфером 0 байт, и Builder бросил бы исключение
                if (!formatOk || size < Short.SIZE_BYTES) return null
                return ShortArray(size / Short.SIZE_BYTES) { buffer.getShort(body + it * Short.SIZE_BYTES) }
            }
        }
        // Чанк нечётной длины дополняется байтом
        pos = body + size + (size and 1)
    }
    return null
}

private fun ascii(bytes: ByteArray, offset: Int): String = String(bytes, offset, 4, Charsets.US_ASCII)

private const val HEADER_SIZE = 12
private const val CHUNK_HEADER_SIZE = 8
private const val FMT_SIZE = 16
private const val FORMAT_PCM = 1
