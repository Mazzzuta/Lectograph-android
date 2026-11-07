package ru.lectograph.app.transcription

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException

internal object AudioDecoder {
    // shortcut: 60-second boundaries may split a phrase; add overlapping chunks after testing lecture recordings.
    fun decode(file: File, cancelled: () -> Boolean, output: (FloatArray, Long, Long) -> Unit) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: throw IOException("В записи нет аудиодорожки")
            val input = extractor.getTrackFormat(track); extractor.selectTrack(track)
            val duration = if (input.containsKey(MediaFormat.KEY_DURATION)) input.getLong(MediaFormat.KEY_DURATION) / 1000 else 0L
            val decoder = MediaCodec.createDecoderByType(requireNotNull(input.getString(MediaFormat.KEY_MIME)))
            codec = decoder; decoder.configure(input, null, null, 0); decoder.start()
            var inputEnded = false; var outputEnded = false
            var resampler: PcmResampler? = null
            val info = MediaCodec.BufferInfo()
            val chunk = FloatArray(16000 * 60)
            var count = 0; var sampleOffset = 0L; var lastOutput = SystemClock.elapsedRealtime()
            while (!outputEnded) {
                if (cancelled()) throw CancellationException()
                if (!inputEnded) {
                    val index = decoder.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val buffer = requireNotNull(decoder.getInputBuffer(index)); buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) { decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true }
                        else { decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0); extractor.advance() }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, 10000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val format = decoder.outputFormat
                    val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) format.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    if (resampler != null) throw IOException("Аудиоформат изменился во время обработки")
                    resampler = PcmResampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), format.getInteger(MediaFormat.KEY_CHANNEL_COUNT), encoding)
                } else if (index >= 0) {
                    lastOutput = SystemClock.elapsedRealtime()
                    try {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val converter = resampler ?: throw IOException("Не удалось определить формат аудио")
                            val pcm = requireNotNull(decoder.getOutputBuffer(index)); pcm.position(info.offset); pcm.limit(info.offset + info.size)
                            converter.append(pcm) { sample ->
                                chunk[count++] = sample
                                if (count == chunk.size) { output(chunk, sampleOffset / 16, duration); sampleOffset += count; count = 0 }
                            }
                            // Inference time is not time spent waiting for decoder buffers.
                            lastOutput = SystemClock.elapsedRealtime()
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally { decoder.releaseOutputBuffer(index, false) }
                } else if (SystemClock.elapsedRealtime() - lastOutput > 30000) throw IOException("Декодирование аудио не отвечает")
            }
            if (count > 0) output(chunk.copyOf(count), sampleOffset / 16, duration)
            else if (sampleOffset == 0L) throw IOException("Запись не содержит звука")
        } finally {
            codec?.let { try { it.stop() } catch (_: IllegalStateException) {} finally { it.release() } }
            extractor.release()
        }
    }
}
