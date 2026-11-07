package ru.lectograph.app.recording

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import ru.lectograph.app.MainActivity
import ru.lectograph.app.R
import ru.lectograph.app.database.LectureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecordingStatus(val id: Long = -1, val paused: Boolean = false, val error: String? = null)

class RecordingService : Service() {
    companion object {
        private val mutableState = MutableStateFlow(RecordingStatus())
        val state = mutableState.asStateFlow()
        @Volatile var activeId = -1L
        @Volatile var startedAt = 0L
        @Volatile var pausedAt = 0L
        @Volatile var pausedMillis = 0L
        @Volatile var error: String? = null
        fun elapsed() = if (activeId == -1L) 0L else ((if (pausedAt == 0L) SystemClock.elapsedRealtime() else pausedAt) - startedAt - pausedMillis).coerceAtLeast(0)
    }
    private var recorder: MediaRecorder? = null
    private lateinit var store: LectureStore
    override fun onCreate() {
        super.onCreate(); store = LectureStore(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("recording", "Запись лекций", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        when (intent.action) {
            "stop" -> { finish(); return START_NOT_STICKY }
            "pause" -> if (recorder != null) {
                try {
                    if (pausedAt == 0L) { recorder?.pause(); pausedAt = SystemClock.elapsedRealtime() }
                    else { recorder?.resume(); pausedMillis += SystemClock.elapsedRealtime() - pausedAt; pausedAt = 0 }
                    mutableState.value = RecordingStatus(activeId, pausedAt != 0L)
                    if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                        getSystemService(NotificationManager::class.java).notify(1, notification())
                } catch (_: RuntimeException) {
                    error = "Не удалось изменить состояние записи"
                    mutableState.value = RecordingStatus(activeId, pausedAt != 0L, error)
                }
                return START_NOT_STICKY
            }
        }
        if (recorder != null) return START_NOT_STICKY
        error = null
        try {
            check(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Microphone permission required" }
            store.recoverInterrupted()
            val dir = File(filesDir, "audio")
            check(dir.isDirectory || dir.mkdirs()) { "Не удалось создать папку записи" }
            check(dir.usableSpace >= 20L * 1024 * 1024) { "Недостаточно места для записи" }
            val audio = File(dir, "${UUID.randomUUID()}.m4a")
            val title = intent.getStringExtra("title").orEmpty().ifBlank { "Запись · " + SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date()) }
            val subject = intent.getStringExtra("subject").orEmpty()
            if (subject.isNotBlank()) store.addSubject(subject)
            activeId = store.create(title, subject, audio.name)
            startedAt = SystemClock.elapsedRealtime(); pausedAt = 0; pausedMillis = 0
            if (Build.VERSION.SDK_INT >= 30) startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(1, notification())
            val recording = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            recorder = recording
            recording.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setAudioSamplingRate(44100); setAudioEncodingBitRate(96000)
                setOutputFile(audio.absolutePath)
                setOnErrorListener { _, _, _ -> error = "Запись прервалась. Проверьте сохранённый файл."; finish() }
                prepare(); start()
            }
            mutableState.value = RecordingStatus(activeId)
        } catch (_: Exception) {
            error = "Не удалось начать запись. Проверьте микрофон и свободное место."
            release(); if (activeId != -1L) store.finish(activeId, 0, "error")
            activeId = -1; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            mutableState.value = RecordingStatus(error = error)
        }
        return START_NOT_STICKY
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 0, Intent(this, RecordingService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "recording").setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(if (pausedAt == 0L) "Идёт запись лекции" else "Запись на паузе")
            .setContentText("Нажмите, чтобы открыть диктофон").setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Завершить", stop).build()).build()
    }
    private fun finish() {
        if (activeId == -1L) { stopSelf(); return }
        val duration = elapsed(); var status = if (error == null) "recorded" else "interrupted"
        try { recorder?.stop() } catch (_: RuntimeException) { status = "error"; error = "Не удалось завершить аудиофайл. Запись могла быть слишком короткой." } finally { release() }
        store.finish(activeId, duration, status); activeId = -1; pausedAt = 0
        mutableState.value = RecordingStatus(error = error)
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    private fun release() { recorder?.release(); recorder = null }
    override fun onDestroy() { if (activeId != -1L) { error = "Запись прервана системой"; finish() }; store.close(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
