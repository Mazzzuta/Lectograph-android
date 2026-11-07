package ru.lectograph.app.transcription

import java.io.File

class WhisperEngine(model: File) : AutoCloseable {
    companion object { init { System.loadLibrary("lectograph-whisper") } }
    private var handle = load(model.absolutePath)
    interface Listener {
        fun onProgress(percent: Int)
        fun onSegment(startMs: Long, endMs: Long, utf8: ByteArray)
    }
    fun transcribe(samples: FloatArray, listener: Listener) = decode(handle, samples, listener)
    @Synchronized fun cancel() { if (handle != 0L) cancelNative(handle) }
    @Synchronized override fun close() { if (handle != 0L) { free(handle); handle = 0 } }
    private external fun load(path: String): Long
    private external fun decode(handle: Long, audio: FloatArray, listener: Listener)
    private external fun cancelNative(handle: Long)
    private external fun free(handle: Long)
}
