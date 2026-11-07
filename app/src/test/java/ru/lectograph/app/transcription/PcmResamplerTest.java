package ru.lectograph.app.transcription;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

public class PcmResamplerTest {
    @Test public void stereoIsAveragedAndResampledAcrossBuffers() throws Exception {
        var resampler = new PcmResampler(48000, 2, 2);
        var result = new ArrayList<Float>();
        for (int part = 0; part < 2; part++) {
            ByteBuffer pcm = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            for (int frame = 0; frame < 3; frame++) { pcm.putShort((short) 16384); pcm.putShort((short) 0); }
            pcm.flip(); resampler.append(pcm, result::add);
        }
        assertEquals(2, result.size()); assertEquals(0.25f, result.get(0), 0.0001f); assertEquals(0.25f, result.get(1), 0.0001f);
    }
    @Test public void floatPcmIsInterpolatedWhenUpsampling() throws Exception {
        var resampler = new PcmResampler(8000, 1, 4);
        var result = new ArrayList<Float>();
        ByteBuffer pcm = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(0).putFloat(1);
        pcm.flip(); resampler.append(pcm, result::add);
        assertArrayEquals(new Float[]{0f, 0.5f, 1f}, result.toArray(new Float[0]));
    }
    @Test public void malformedPcmIsRejected() throws Exception {
        try { new PcmResampler(16000, 1, 2).append(ByteBuffer.allocate(1), value -> {}); fail("Partial sample accepted"); }
        catch (IllegalArgumentException expected) { }
    }
}
