package ru.lectograph.app.transcription;

import java.io.File;

public final class WhisperEngine implements AutoCloseable {
    static { System.loadLibrary("lectograph-whisper"); }
    private long handle;
    public interface Listener {
        void onProgress(int percent);
        void onSegment(long startMs, long endMs, byte[] utf8);
    }
    public WhisperEngine(File model) { handle = load(model.getAbsolutePath()); }
    public void transcribe(float[] samples, Listener listener) { decode(handle, samples, listener); }
    public synchronized void cancel() { if (handle != 0) cancelNative(handle); }
    @Override public synchronized void close() { if (handle != 0) { free(handle); handle = 0; } }
    private static native long load(String path);
    private static native void decode(long handle, float[] audio, Listener listener);
    private static native void cancelNative(long handle);
    private static native void free(long handle);
}
