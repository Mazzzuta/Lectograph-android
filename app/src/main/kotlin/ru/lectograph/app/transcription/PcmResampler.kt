package ru.lectograph.app.transcription

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class PcmResampler(private val rate: Int, private val channels: Int, private val encoding: Int) {
    init { require(rate in 8000..192000 && channels in 1..8 && encoding in listOf(2, 4)) { "Неподдерживаемый формат звука" } }
    private var index = 0L
    private var next = 0.0
    private var previous = 0f
    fun append(pcm: ByteBuffer, sink: (Float) -> Unit) {
        pcm.order(ByteOrder.LITTLE_ENDIAN)
        val frameBytes = channels * if (encoding == 4) 4 else 2
        require(pcm.remaining() % frameBytes == 0) { "Повреждённый аудиопоток" }
        while (pcm.remaining() >= frameBytes) {
            var sample = 0f
            repeat(channels) { sample += if (encoding == 4) pcm.float else pcm.short / 32768f }
            sample /= channels
            sample = if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f
            while (next <= index) {
                sink(if (index == 0L) sample else previous + (sample - previous) * (next - (index - 1)).toFloat())
                next += rate / 16000.0
            }
            previous = sample; index++
        }
    }
}
