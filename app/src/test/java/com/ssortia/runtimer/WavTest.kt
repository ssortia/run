package com.ssortia.runtimer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavTest {
    private fun chunk(id: String, body: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .put(id.toByteArray(Charsets.US_ASCII)).putInt(body.size).array()
        // Чанк нечётной длины дополняется байтом
        return header + body + if (body.size % 2 == 1) byteArrayOf(0) else byteArrayOf()
    }

    private fun fmt(format: Int = 1, channels: Int = 1, rate: Int = SAMPLE_RATE_HZ, bits: Int = 16): ByteArray {
        val blockAlign = channels * bits / 8
        return ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(format.toShort()).putShort(channels.toShort()).putInt(rate)
            .putInt(rate * blockAlign).putShort(blockAlign.toShort()).putShort(bits.toShort())
            .array()
    }

    private fun samples(vararg values: Short): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putShort(it) }
        return buffer.array()
    }

    private fun wav(vararg chunks: ByteArray): ByteArray {
        val body = "WAVE".toByteArray(Charsets.US_ASCII) + chunks.fold(byteArrayOf()) { acc, c -> acc + c }
        return chunk("RIFF", body)
    }

    @Test
    fun `сэмплы читаются из чанка data`() {
        val bytes = wav(chunk("fmt ", fmt()), chunk("data", samples(0, 1000, -1000, Short.MAX_VALUE)))

        assertArrayEquals(shortArrayOf(0, 1000, -1000, Short.MAX_VALUE), decodeWav(bytes))
    }

    @Test
    fun `посторонние чанки пропускаются, в том числе нечётной длины`() {
        val bytes = wav(
            chunk("LIST", byteArrayOf(1, 2, 3)),
            chunk("fmt ", fmt()),
            chunk("junk", byteArrayOf(9)),
            chunk("data", samples(5, -5)),
        )

        assertArrayEquals(shortArrayOf(5, -5), decodeWav(bytes))
    }

    @Test
    fun `другой формат отклоняется`() {
        val data = chunk("data", samples(1, 2))

        assertNull(decodeWav(wav(chunk("fmt ", fmt(channels = 2)), data)))
        assertNull(decodeWav(wav(chunk("fmt ", fmt(rate = 48_000)), data)))
        assertNull(decodeWav(wav(chunk("fmt ", fmt(bits = 8)), data)))
        assertNull(decodeWav(wav(chunk("fmt ", fmt(format = 3)), data)))
    }

    @Test
    fun `файл без fmt перед data или без data отклоняется`() {
        assertNull(decodeWav(wav(chunk("data", samples(1)), chunk("fmt ", fmt()))))
        assertNull(decodeWav(wav(chunk("fmt ", fmt()))))
    }

    @Test
    fun `повреждённый файл отклоняется`() {
        assertNull(decodeWav(byteArrayOf()))
        assertNull(decodeWav("RIFF0000WAVX".toByteArray(Charsets.US_ASCII)))
        // Размер data больше остатка файла
        val truncated = wav(chunk("fmt ", fmt()), chunk("data", samples(1, 2, 3))).dropLast(2).toByteArray()
        assertNull(decodeWav(truncated))
    }

    @Test
    fun `пустой чанк data отклоняется`() {
        assertNull(decodeWav(wav(chunk("fmt ", fmt()), chunk("data", byteArrayOf()))))
        // Один байт не даёт ни одного сэмпла
        assertNull(decodeWav(wav(chunk("fmt ", fmt()), chunk("data", byteArrayOf(1)))))
    }
}
