package ru.lectograph.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import ru.lectograph.app.backup.BackupArchive
import ru.lectograph.app.database.LectureStore
import ru.lectograph.app.recording.RecordingService
import ru.lectograph.app.transcription.TranscriptionService
import ru.lectograph.app.ai.StudyService
import ru.lectograph.app.ui.components.GlassCard

@Composable fun BackupPanel(unavailable: Boolean, working: (Boolean) -> Unit, restored: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<BackupArchive.Archive?>(null) }
    var message by remember { mutableStateOf("") }
    fun idle() = RecordingService.activeId == -1L && !TranscriptionService.running && !StudyService.running
    fun run(block: suspend () -> Unit) {
        if (BackupArchive.busy || !idle()) { message = "Сначала завершите запись и обработку."; return }
        BackupArchive.busy = true; progress = true; working(true)
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "Не удалось обработать копию: проверьте формат файла, доступ и свободное место." }
            finally { BackupArchive.busy = false; progress = false; working(false) }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) run {
            withContext(Dispatchers.IO) {
                val json = LectureStore(context).use { BackupArchive.encode(it.backup()) }
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            message = "Резервная копия сохранена. Аудио и модель в неё не входят."
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run {
            pending = withContext(Dispatchers.IO) { BackupArchive.read(requireNotNull(context.contentResolver.openInputStream(uri))) }
            message = ""
        }
    }
    GlassCard {
        Text("Резервная копия", style = MaterialTheme.typography.titleMedium)
        Text("Предметы, лекции, тексты, конспекты, тесты и заметки. Без аудио, модели и настроек темы.", style = MaterialTheme.typography.bodySmall)
        TextButton({ export.launch("Lectograph-backup.json") }, enabled = !unavailable && !progress) { Text("Экспортировать копию") }
        TextButton({ import.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, enabled = !unavailable && !progress) { Text("Импортировать копию") }
        if (progress) Text("Обработка копии…")
        if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall)
    }
    pending?.let { archive -> AlertDialog(onDismissRequest = { if (!progress) pending = null }, title = { Text("Заменить библиотеку?") },
        text = { Column {
            Text("В копии: предметов — ${archive.subjects.size}, лекций — ${archive.lectures.size}.")
            Text("Текущие предметы и лекции будут заменены. Сначала экспортируйте свою библиотеку, если она нужна. Аудио из текущей библиотеки останется во внутреннем хранилище, но не будет связано с импортированными лекциями.")
        } }, confirmButton = { TextButton(enabled = !unavailable && !progress, onClick = {
            run {
                withContext(Dispatchers.IO) { LectureStore(context).use { it.restore(archive) } }
                pending = null; restored(); message = "Библиотека восстановлена. Аудио в копии отсутствует."
            }
        }) { Text("Заменить библиотеку") } }, dismissButton = { TextButton({ pending = null }, enabled = !progress) { Text("Отмена") } }) }
}
