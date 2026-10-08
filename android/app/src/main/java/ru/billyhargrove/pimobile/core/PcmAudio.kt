package ru.billyhargrove.pimobile.core

import kotlin.math.sqrt

/** Shared PCM16 mono wire bounds and little-endian meter; no audio device or UI. */
internal object PcmAudio {
    const val SAMPLE_RATE = 16000
    const val MAX_SECONDS = 600
    const val MAX_BYTES = SAMPLE_RATE * 2 * MAX_SECONDS
    const val MIN_BYTES = 3200
    const val CHUNK_BYTES = 3200
    data class Meter(val seconds: Int, val level: Float)
    fun validSize(bytes: Long) = bytes in MIN_BYTES.toLong()..MAX_BYTES.toLong() && bytes % 2 == 0L
    fun level(bytes: ByteArray, count: Int): Float {
        require(count in 0..bytes.size && count % 2 == 0) { "Invalid PCM16 capture" }
        var sum = 0.0
        for (i in 0 until count step 2) {
            val sample = ((bytes[i].toInt() and 255) or (bytes[i + 1].toInt() shl 8)).toShort() / 32768.0
            sum += sample * sample
        }
        return minOf(1.0, sqrt(sum / maxOf(1, count / 2)) * 4).toFloat()
    }
    fun meter(bytes: ByteArray, count: Int, total: Int) = Meter(total / (SAMPLE_RATE * 2), level(bytes, count))
}
