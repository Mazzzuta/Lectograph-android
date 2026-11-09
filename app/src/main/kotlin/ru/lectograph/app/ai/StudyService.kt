package ru.lectograph.app.ai

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.lectograph.app.MainActivity
import ru.lectograph.app.R
import ru.lectograph.app.database.LectureStore
import ru.lectograph.app.transcription.TranscriptionService

data class StudyStatus(val running: Boolean = false, val lectureId: Long = -1, val message: String = "")

class StudyService : Service() {
    companion object {
        private val mutableState = MutableStateFlow(StudyStatus())
        val state = mutableState.asStateFlow()
        val running get() = state.value.running
        val lectureId get() = state.value.lectureId
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ru.lectograph.app.backup.BackupArchive.busy) { if (!running) stopSelf(); return START_NOT_STICKY }
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        val action = intent.getStringExtra("action") ?: run { stopSelf(); return START_NOT_STICKY }
        if (action !in listOf("study", "test", "question")) { stopSelf(); return START_NOT_STICKY }
        val id = intent.getLongExtra("lecture", -1)
        if (TranscriptionService.running && TranscriptionService.lectureId == id) { stopSelf(); return START_NOT_STICKY }
        mutableState.value = StudyStatus(true, id, "Обработка через Gemini…")
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("study", "Обработка конспектов", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "study").setSmallIcon(R.drawable.ic_mic).setContentTitle(state.value.message).setContentIntent(open).setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(3, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(3, notification)
        } catch (_: RuntimeException) { mutableState.update { it.copy(running = false, message = "Не удалось запустить обработку") }; stopSelf(); return START_NOT_STICKY }
        scope.launch {
            try {
                LectureStore(this@StudyService).use { store ->
                    val text = store.transcriptText(id)
                    val result = GeminiClient.request(this@StudyService, action, text, intent.getStringExtra("question").orEmpty())
                    store.saveMaterial(id, action, result, text)
                    mutableState.update { it.copy(message = "Материалы сохранены на устройстве") }
                }
            } catch (e: Exception) { mutableState.update { it.copy(message = e.message ?: "Не удалось обработать лекцию") } }
            finally { withContext(NonCancellable + Dispatchers.Main) { mutableState.update { it.copy(running = false) }; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
