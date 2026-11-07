package ru.lectograph.app.transcription;

import android.media.*;
import android.os.SystemClock;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

final class AudioDecoder {
    interface Output { void accept(float[] samples, long startMs, long durationMs) throws Exception; }
    // shortcut: 60-second boundaries may split a phrase; add overlapping chunks after testing lecture recordings.
    static void decode(File file, BooleanSupplier cancelled, Output output) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(file.getAbsolutePath());
            MediaFormat inputFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i);
                if (candidate.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                    inputFormat = candidate; extractor.selectTrack(i); break;
                }
            }
            if (inputFormat == null) throw new IOException("В записи нет аудиодорожки");
            long duration = inputFormat.containsKey(MediaFormat.KEY_DURATION) ? inputFormat.getLong(MediaFormat.KEY_DURATION) / 1000 : 0;
            codec = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
            codec.configure(inputFormat, null, null, 0); codec.start();
            boolean inputEnded = false, outputEnded = false;
            PcmResampler resampler = null;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            float[] chunk = new float[16000 * 60];
            int[] count = {0}; long[] sampleOffset = {0};
            long lastOutput = SystemClock.elapsedRealtime();
            while (!outputEnded) {
                if (cancelled.getAsBoolean()) throw new CancellationException();
                if (!inputEnded) {
                    int index = codec.dequeueInputBuffer(10000);
                    if (index >= 0) {
                        ByteBuffer buffer = codec.getInputBuffer(index); buffer.clear();
                        int size = extractor.readSampleData(buffer, 0);
                        if (size < 0) { codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true; }
                        else { codec.queueInputBuffer(index, 0, size, extractor.getSampleTime(), 0); extractor.advance(); }
                    }
                }
                int index = codec.dequeueOutputBuffer(info, 10000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = codec.getOutputFormat();
                    int encoding = format.containsKey(MediaFormat.KEY_PCM_ENCODING) ? format.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;
                    if (resampler != null) throw new IOException("Аудиоформат изменился во время обработки");
                    resampler = new PcmResampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE), format.getInteger(MediaFormat.KEY_CHANNEL_COUNT), encoding);
                } else if (index >= 0) {
                    lastOutput = SystemClock.elapsedRealtime();
                    try {
                        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            if (resampler == null) throw new IOException("Не удалось определить формат аудио");
                            ByteBuffer pcm = codec.getOutputBuffer(index);
                            pcm.position(info.offset); pcm.limit(info.offset + info.size);
                            resampler.append(pcm, sample -> {
                                chunk[count[0]++] = sample;
                                if (count[0] == chunk.length) {
                                    output.accept(chunk, sampleOffset[0] / 16, duration);
                                    sampleOffset[0] += count[0]; count[0] = 0;
                                }
                            });
                            // Inference can take minutes; decoder timeout only counts time waiting for audio buffers.
                            lastOutput = SystemClock.elapsedRealtime();
                        }
                        outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    } finally { codec.releaseOutputBuffer(index, false); }
                } else if (SystemClock.elapsedRealtime() - lastOutput > 30000) throw new IOException("Декодирование аудио не отвечает");
            }
            if (count[0] > 0) output.accept(Arrays.copyOf(chunk, count[0]), sampleOffset[0] / 16, duration);
            else if (sampleOffset[0] == 0) throw new IOException("Запись не содержит звука");
        } finally {
            if (codec != null) { try { codec.stop(); } catch (IllegalStateException ignored) {} codec.release(); }
            extractor.release();
        }
    }
    private AudioDecoder() {}
}
