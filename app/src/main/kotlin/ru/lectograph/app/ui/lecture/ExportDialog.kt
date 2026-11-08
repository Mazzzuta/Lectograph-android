package ru.lectograph.app.ui.lecture

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import ru.lectograph.app.export.DocxWriter
import ru.lectograph.app.models.Lecture
import ru.lectograph.app.models.TranscriptSegment
import ru.lectograph.app.utils.LectureFormat

@Composable fun ExportDialog(lecture: Lecture, segments: List<TranscriptSegment>, materials: Map<String, String>, dismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notes by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf("Полный текст") }
    var busy by remember { mutableStateOf(false) }
    val study = remember(materials) { runCatching { JSONObject(materials["study"].orEmpty()) }.getOrNull() }
    val choices = linkedMapOf("Полный текст" to segments.joinToString(" ") { it.text.trim() })
    study?.optString("short")?.takeIf { it.isNotBlank() }?.let { choices["Краткий конспект"] = it }
    study?.optString("detailed")?.takeIf { it.isNotBlank() }?.let { choices["Подробный конспект"] = it }
    val text = choices[selected].orEmpty()
    val mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    suspend fun export(output: java.io.OutputStream) = withContext(Dispatchers.IO) {
        DocxWriter.write(output, lecture.title, "${lecture.subject} · ${LectureFormat.date(lecture.created)} · $selected", text, if (notes) lecture.notes else "")
    }
    fun failure() { Toast.makeText(context, "Не удалось экспортировать документ. Проверьте доступное место и повторите.", Toast.LENGTH_LONG).show() }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try { export(context.contentResolver.openOutputStream(uri) ?: error("Нет доступа")); Toast.makeText(context, "Документ сохранён", Toast.LENGTH_SHORT).show(); dismiss() }
            catch (_: Exception) { failure() } finally { busy = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Экспорт в DOCX") }, text = {
        Column {
            choices.forEach { (label, _) -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected == label, { selected = label }, enabled = !busy); Text(label)
            } }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(notes, { notes = it }, enabled = !busy); Text("Добавить мои заметки") }
            if (text.isBlank()) Text("Сначала распознайте запись.")
            if (busy) Text("Подготовка документа…")
        }
    }, confirmButton = {
        TextButton(enabled = !busy && text.isNotBlank(), onClick = { save.launch("Лекция.docx") }) { Text("Сохранить") }
        TextButton(enabled = !busy && text.isNotBlank(), onClick = {
            scope.launch {
                busy = true
                try {
                    val file = File(context.cacheDir, "exports/Лекция-${java.util.UUID.randomUUID()}.docx")
                    withContext(Dispatchers.IO) { file.parentFile!!.mkdirs() }
                    export(withContext(Dispatchers.IO) { file.outputStream() })
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".exports", file)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Поделиться лекцией")); dismiss()
                } catch (_: Exception) { failure() } finally { busy = false }
            }
        }) { Text("Поделиться") }
    }, dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("Отмена") } })
}
