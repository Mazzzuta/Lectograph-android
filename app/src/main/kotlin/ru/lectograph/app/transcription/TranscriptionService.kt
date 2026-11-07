package ru.lectograph.app.transcription

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import ru.lectograph.app.MainActivity
import ru.lectograph.app.R
import ru.lectograph.app.database.LectureStore
import ru.lectograph.app.models.TranscriptSegment

data class ProcessingStatus(val running: Boolean = false, val lectureId: Long = -1, val progress: Int = 0, val message: String = "")

class TranscriptionService : Service() {
    companion object {
        private val mutableState = MutableStateFlow(ProcessingStatus())
        val state = mutableState.asStateFlow()
        val running get() = state.value.running
        val lectureId get() = state.value.lectureId
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cancelled = AtomicBoolean()
    @Volatile private var engine: WhisperEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastProgress = -1
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("transcription", "Распознавание лекций", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        if (intent.action == "cancel") { cancel(); if (!running) stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        val download = intent.action == "download"
        val id = if (download) -1 else intent.getLongExtra("lecture", -1)
        cancelled.set(false); lastProgress = -1
        mutableState.value = ProcessingStatus(true, id, 0, if (download) "Загрузка Whisper base" else "Подготовка распознавания")
        try {
            val type = if (Build.VERSION.SDK_INT >= 35 && !download) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (Build.VERSION.SDK_INT >= 29) startForeground(2, notification(), type) else startForeground(2, notification())
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lectograph:transcription").apply { acquire(6 * 60 * 60 * 1000L) }
        } catch (_: RuntimeException) {
            mutableState.update { it.copy(running = false, message = "Не удалось запустить фоновую обработку") }; stopSelf(); return START_NOT_STICKY
        }
        scope.launch {
            try {
                LectureStore(this@TranscriptionService).use { store ->
                    try {
                        if (download) {
                            WhisperModel.download(this@TranscriptionService, cancelled::get) { update(it, "Загрузка Whisper base") }
                            mutableState.update { it.copy(message = "Whisper base готова. Можно распознать запись.") }
                        } else {
                            check(WhisperModel.ready(this@TranscriptionService)) { "Сначала скачайте Whisper base" }
                            val lecture = store.get(id)
                            check(lecture != null && lecture.status != "recording") { "Запись недоступна для обработки" }
                            val audio = File(File(filesDir, "audio"), lecture.audio)
                            check(audio.isFile) { "Аудиофайл не найден" }
                            store.setJob(id, "running", 0, "")
                            val segments = mutableListOf<TranscriptSegment>()
                            try {
                                WhisperEngine(WhisperModel.file(this@TranscriptionService)).use { loaded ->
                                    engine = loaded; if (cancelled.get()) loaded.cancel()
                                    AudioDecoder.decode(audio, cancelled::get) { samples, startMs, durationMs ->
                                        val total = maxOf(durationMs, startMs + samples.size / 16L)
                                        loaded.transcribe(samples, object : WhisperEngine.Listener {
                                            override fun onProgress(percent: Int) {
                                                val overall = minOf(99, (100 * (startMs + samples.size / 16L * percent / 100) / maxOf(1, total)).toInt())
                                                update(overall, "Распознавание лекции", store, id)
                                            }
                                            override fun onSegment(startMsInChunk: Long, endMs: Long, utf8: ByteArray) {
                                                val text = utf8.toString(Charsets.UTF_8).trim()
                                                if (text.isNotEmpty()) segments.add(TranscriptSegment(startMs + startMsInChunk, startMs + endMs, text))
                                            }
                                        })
                                    }
                                }
                            } finally { engine = null }
                            if (cancelled.get()) throw CancellationException()
                            store.saveTranscript(id, segments)
                            mutableState.update { it.copy(progress = 100, message = if (segments.isEmpty()) "Речь в записи не найдена" else "Текст лекции сохранён") }
                        }
                    } catch (_: CancellationException) {
                        val message = "Обработка отменена. Аудио сохранено."
                        mutableState.update { it.copy(message = message) }
                        if (!download) store.setJob(id, "cancelled", state.value.progress, message)
                    } catch (e: Throwable) {
                        if (e !is Exception && e !is LinkageError && e !is OutOfMemoryError) throw e
                        val message = if (e is OutOfMemoryError) "Недостаточно оперативной памяти. Закройте другие приложения и повторите попытку." else e.message ?: "Не удалось обработать запись. Повторите попытку."
                        mutableState.update { it.copy(message = message) }
                        if (!download) store.setJob(id, "error", state.value.progress, message)
                    }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    wakeLock?.let { if (it.isHeld) it.release() }
                    mutableState.update { it.copy(running = false) }; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }
    private fun update(percent: Int, message: String, store: LectureStore? = null, id: Long = -1) {
        if (percent == lastProgress) return
        lastProgress = percent; mutableState.update { it.copy(progress = percent, message = message) }
        store?.setJob(id, "running", percent, "")
        if (running && (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED))
            getSystemService(NotificationManager::class.java).notify(2, notification())
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(this, 2, Intent(this, TranscriptionService::class.java).setAction("cancel"), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "transcription").setSmallIcon(R.drawable.ic_mic).setContentTitle(state.value.message)
            .setContentText("${state.value.progress}%").setProgress(100, state.value.progress, state.value.progress == 0).setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Отменить", cancel).build()).build()
    }
    private fun cancel() { cancelled.set(true); engine?.cancel() }
    override fun onTimeout(startId: Int, type: Int) { cancel(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { cancel(); scope.cancel(); wakeLock?.let { if (it.isHeld) it.release() }; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
