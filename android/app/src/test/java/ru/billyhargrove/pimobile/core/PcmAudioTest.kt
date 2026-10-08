package ru.billyhargrove.pimobile.core

import org.junit.Assert.*
import org.junit.Test

class PcmAudioTest {
    @Test fun canonicalBoundsMatchTenMinutesAndOneTenthSecond() {
        assertEquals(16000, PcmAudio.SAMPLE_RATE); assertEquals(19_200_000, PcmAudio.MAX_BYTES)
        assertTrue(PcmAudio.validSize(3200)); assertTrue(PcmAudio.validSize(19_200_000))
        for (size in listOf(-1L, 0L, 3198L, 3201L, 19_200_002L, Long.MAX_VALUE)) assertFalse(PcmAudio.validSize(size))
    }
    @Test fun silenceAndEmptyBlockStayFlat() { assertEquals(0f, PcmAudio.level(ByteArray(3200), 3200)); assertEquals(0f, PcmAudio.level(byteArrayOf(), 0)) }
    @Test fun littleEndianSignedPositiveAndNegativeSamplesHaveEqualRms() {
        val positive = byteArrayOf(0, 32); val negative = byteArrayOf(0, -32)
        assertEquals(1f, PcmAudio.level(positive, 2)); assertEquals(PcmAudio.level(positive, 2), PcmAudio.level(negative, 2))
        assertEquals(0.5f, PcmAudio.level(byteArrayOf(0, 16), 2)); assertEquals(1f, PcmAudio.level(byteArrayOf(0, -128), 2))
    }
    @Test fun rmsUsesOnlyReadBytesAndRetainsOldGain() {
        val bytes = byteArrayOf(0, 16, 0, 0, -1, 127)
        assertEquals((kotlin.math.sqrt(.125 * .125 / 2) * 4).toFloat(), PcmAudio.level(bytes, 4), .000001f)
        assertEquals(0.5f, PcmAudio.level(bytes, 2))
    }
    @Test fun partialOrOutOfBoundsSamplesFailClosed() {
        for (count in listOf(-2, 1, 3, 6)) {
            try { PcmAudio.level(ByteArray(4), count); fail("Invalid count accepted: $count") } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun durationUsesCapturedSamplesRatherThanWallClock() {
        val bytes = byteArrayOf(0, 16)
        assertEquals(PcmAudio.Meter(0, .5f), PcmAudio.meter(bytes, 2, 31998))
        assertEquals(PcmAudio.Meter(1, .5f), PcmAudio.meter(bytes, 2, 32000))
        assertEquals(PcmAudio.Meter(600, .5f), PcmAudio.meter(bytes, 2, PcmAudio.MAX_BYTES))
    }
}
