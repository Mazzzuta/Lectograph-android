package ru.lectograph.app.ui.settings

import android.app.ActivityManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.lectograph.app.BuildConfig
import ru.lectograph.app.ai.StudyService
import ru.lectograph.app.recording.RecordingService
import ru.lectograph.app.transcription.TranscriptionService
import ru.lectograph.app.ui.components.*
import java.io.File
import java.util.Locale

internal fun storageBytes(directory: File): Long = directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
private fun sizeLabel(bytes: Long) = String.format(Locale.getDefault(), "%.1f МБ", bytes / (1024.0 * 1024.0))

@Composable fun SettingsScreen(theme: String, choose: (String) -> Unit, restored: () -> Unit = {}) {
    val context = LocalContext.current
    val recording by RecordingService.state.collectAsStateWithLifecycle()
    val processing by TranscriptionService.state.collectAsStateWithLifecycle()
    val study by StudyService.state.collectAsStateWithLifecycle()
    var backupBusy by remember { mutableStateOf(false) }
    val busy = backupBusy || recording.id != -1L || processing.running || study.running
    var confirming by rememberSaveable { mutableStateOf(false) }
    var storage by remember { mutableStateOf<Map<String, Long>?>(null) }
    var storageError by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        try {
            storage = withContext(Dispatchers.IO) { linkedMapOf(
                "Аудиозаписи" to storageBytes(File(context.filesDir, "audio")),
                "Модель распознавания" to storageBytes(File(context.filesDir, "models")),
                "Тексты и данные" to storageBytes(File(context.applicationInfo.dataDir, "databases")),
                "Кэш и документы для отправки" to storageBytes(context.cacheDir)
            ) }
            storageError = false
        } catch (_: java.io.IOException) { storageError = true }
    }
    Page {
        item { Heading("Настройки") }
        item { GlassCard {
            Text("Цветовая схема", style = MaterialTheme.typography.titleMedium)
            listOf("system" to "Как в системе", "dark" to "Графитовая", "light" to "Светлая").forEach { (key, title) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(theme == key, role = Role.RadioButton, onClick = { choose(key) }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(theme == key, onClick = null); Text(title)
                }
            }
        } }
        item { GlassCard {
            Text("Хранилище", style = MaterialTheme.typography.titleMedium)
            if (storageError) Text("Не удалось посчитать занятое место.")
            else if (storage == null) Text("Подсчёт занятого места…")
            storage?.forEach { (label, bytes) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(sizeLabel(bytes), style = MaterialTheme.typography.bodySmall)
            } }
            Text("Размеры приблизительные. Аудио распознаётся на телефоне. Конспекты и тесты создаёт подключённый сервер обработки.", style = MaterialTheme.typography.bodySmall)
            Text("Лекцию можно сохранить или отправить в DOCX из её подробного просмотра.", style = MaterialTheme.typography.bodySmall)
        } }
        item { BackupPanel(busy, { backupBusy = it }, restored) }
        item { GlassCard {
            Text("Удаление данных", style = MaterialTheme.typography.titleMedium)
            Text("Удаляет лекции, предметы, заметки, модель Whisper и настройки с этого устройства.", style = MaterialTheme.typography.bodySmall)
            TextButton({ confirming = true }, enabled = !busy) { Text("Удалить все данные", color = MaterialTheme.colorScheme.error) }
            if (busy) Text("Сначала завершите запись и обработку.", style = MaterialTheme.typography.bodySmall)
        } }
        item { GlassCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Лектограф", style = MaterialTheme.typography.titleMedium)
                    Text("Версия приложения", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f))) {
                    Text(BuildConfig.VERSION_NAME, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        } }
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("Удалить все данные приложения?") },
        text = { Text("Это действие нельзя отменить. Будут удалены все лекции, аудио, тексты, заметки, предметы и модель распознавания. Приложение закроется. Документы, сохранённые вами вне приложения, останутся.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            if (RecordingService.activeId == -1L && !TranscriptionService.running && !StudyService.running) {
                val cleared = context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
                if (!cleared) Toast.makeText(context, "Не удалось очистить данные. Повторите через настройки Android.", Toast.LENGTH_LONG).show()
            }
            confirming = false
        }) { Text("Удалить всё", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirming = false }) { Text("Отмена") } })
}
