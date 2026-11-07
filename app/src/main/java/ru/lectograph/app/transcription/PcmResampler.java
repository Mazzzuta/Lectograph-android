package ru.lectograph.app.transcription;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class PcmResampler {
    interface Sink { void accept(float sample) throws Exception; }
    private final int rate, channels, encoding;
    private long index;
    private double next;
    private float previous;
    PcmResampler(int rate, int channels, int encoding) {
        if (rate < 8000 || rate > 192000 || channels < 1 || channels > 8 || (encoding != 2 && encoding != 4))
            throw new IllegalArgumentException("Неподдерживаемый формат звука");
        this.rate = rate; this.channels = channels; this.encoding = encoding;
    }
    void append(ByteBuffer pcm, Sink sink) throws Exception {
        pcm.order(ByteOrder.LITTLE_ENDIAN);
        int frameBytes = channels * (encoding == 4 ? 4 : 2);
        if (pcm.remaining() % frameBytes != 0) throw new IllegalArgumentException("Повреждённый аудиопоток");
        while (pcm.remaining() >= frameBytes) {
            float sample = 0;
            for (int i = 0; i < channels; i++) sample += encoding == 4 ? pcm.getFloat() : pcm.getShort() / 32768f;
            sample /= channels;
            if (!Float.isFinite(sample)) sample = 0;
            sample = Math.max(-1, Math.min(1, sample));
            while (next <= index) {
                float value = index == 0 ? sample : previous + (sample - previous) * (float) (next - (index - 1));
                sink.accept(value); next += rate / 16000.0;
            }
            previous = sample; index++;
        }
    }
}
