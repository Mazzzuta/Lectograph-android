package ru.lectograph.app.transcription

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class PcmResamplerTest {
    @Test fun stereoIsAveragedAcrossBuffers() {
        val resampler = PcmResampler(48000, 2, 2); val result = mutableListOf<Float>()
        repeat(2) {
            val pcm = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            repeat(3) { pcm.putShort(16384); pcm.putShort(0) }
            pcm.flip(); resampler.append(pcm) { result.add(it) }
        }
        assertEquals(listOf(0.25f, 0.25f), result)
    }
    @Test fun floatPcmIsInterpolated() {
        val result = mutableListOf<Float>()
        val pcm = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(0f).putFloat(1f)
        pcm.flip(); PcmResampler(8000, 1, 4).append(pcm) { result.add(it) }
        assertEquals(listOf(0f, 0.5f, 1f), result)
    }
    @Test fun malformedPcmIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { PcmResampler(16000, 1, 2).append(ByteBuffer.allocate(1)) {} }
    }
}
