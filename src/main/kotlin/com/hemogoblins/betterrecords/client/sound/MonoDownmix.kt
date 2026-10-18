package com.hemogoblins.betterrecords.client.sound

import org.lwjgl.BufferUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat

/**
 * OpenAL only positions mono sounds in 3D, stereo buffers always play "inside your head".
 * Every positional sound in vanilla is mono for the same reason, so records are mixed down to mono
 * before they're handed to OpenAL.
 */
object MonoDownmix {

    /** Result of a downmix: the PCM data and the format that now describes it. */
    class Result(@JvmField val data: ByteBuffer, @JvmField val format: AudioFormat)

    @JvmStatic
    fun toMono(data: ByteBuffer, format: AudioFormat): Result {
        if (format.channels != 2 || format.sampleSizeInBits != 16) return Result(data, format)

        val order = if (format.isBigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        val input = data.duplicate().order(order).asShortBuffer()
        val frames = input.remaining() / 2

        val output = BufferUtils.createByteBuffer(frames * 2).order(order)
        for (i in 0 until frames) {
            val left = input.get().toInt()
            val right = input.get().toInt()
            output.putShort(((left + right) / 2).toShort())
        }
        output.flip()

        val mono = AudioFormat(format.sampleRate, 16, 1, true, format.isBigEndian)
        return Result(output, mono)
    }

    /** Length of a PCM buffer in seconds. */
    @JvmStatic
    fun durationSeconds(data: ByteBuffer, format: AudioFormat): Float {
        val bytesPerSecond = format.sampleRate * format.channels * (format.sampleSizeInBits / 8)
        return if (bytesPerSecond <= 0f) 0f else data.remaining() / bytesPerSecond
    }
}
