package ru.lectograph.app.ui.lecture

import android.media.MediaPlayer
import ru.lectograph.app.R
import androidx.compose.ui.res.painterResource
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.io.File
import ru.lectograph.app.ai.StudyStatus
import ru.lectograph.app.models.Lecture
import ru.lectograph.app.transcription.ProcessingStatus
import ru.lectograph.app.transcription.WhisperModel
import ru.lectograph.app.ui.components.*
import ru.lectograph.app.utils.LectureFormat
import ru.lectograph.app.viewmodels.LibraryState
import ru.lectograph.app.viewmodels.LecturesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LectureScreen(lecture: Lecture, data: LibraryState, tab: String, processing: ProcessingStatus, study: StudyStatus,
    vm: LecturesViewModel, edit: () -> Unit, process: (String) -> Unit, generate: (String, String) -> Unit) {
    val context = LocalContext.current
    var player by remember(lecture.id) { mutableStateOf<MediaPlayer?>(null) }
    var exporting by rememberSaveable(lecture.id) { mutableStateOf(false) }
    var deleting by rememberSaveable(lecture.id) { mutableStateOf(false) }
    var showSegments by rememberSaveable(lecture.id) { mutableStateOf(false) }
    DisposableEffect(lecture.id) { onDispose { player?.release() } }
    val play: (Long) -> Unit = { time ->
        player?.release(); player = null
        try {
            val media = MediaPlayer(); player = media
            media.setDataSource(File(File(context.filesDir, "audio"), lecture.audio).absolutePath)
            media.prepare(); media.seekTo(time, MediaPlayer.SEEK_CLOSEST)
            media.setOnCompletionListener { it.release(); if (player == it) player = null }; media.start()
        } catch (_: Exception) { player?.release(); player = null; Toast.makeText(context, "Аудиофайл недоступен или повреждён", Toast.LENGTH_LONG).show() }
    }
    val hasAudio = lecture.audio.isNotEmpty() && File(File(context.filesDir, "audio"), lecture.audio).isFile
    val tabs = listOf("text" to "Текст", "summary" to "Конспект", "review" to "Разбор", "notes" to "Заметки", "test" to "Тест")
    Page {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(vm::closeLecture) { Icon(painterResource(R.drawable.nav_back), "Назад") }
            Row {
                IconButton(edit) { Icon(painterResource(R.drawable.nav_edit), "Изменить название и предмет") }
                IconButton({ exporting = true }, enabled = data.transcript.isNotEmpty()) { Icon(painterResource(R.drawable.nav_export), "Экспорт в DOCX") }
            }
        } }
        item { Heading(lecture.title, "${lecture.subject.ifEmpty { "ЧЕРНОВИК" }} · ${LectureFormat.date(lecture.created)} · ${LectureFormat.time(lecture.duration)}") }
        item { GlassButton(if (player == null) "▶ Прослушать запись" else "■ Остановить воспроизведение", enabled = hasAudio) {
            if (player != null) { player?.release(); player = null } else play(0)
        } }
        item { ScrollableTabRow(tabs.indexOfFirst { it.first == tab }.coerceAtLeast(0), containerColor = androidx.compose.ui.graphics.Color.Transparent, edgePadding = androidx.compose.ui.unit.Dp(0f)) {
            tabs.forEach { (key, title) -> Tab(selected = tab == key, onClick = { vm.selectTab(key) }, text = { Text(title) }) }
        } }
        when (tab) {
            "notes" -> item { LectureNotes(lecture) { vm.notes(lecture.id, it) } }
            "text" -> transcriptPanel(data.transcript, data.job, processing, WhisperModel.ready(context), study.running, showSegments, { showSegments = !showSegments },
                hasAudio = hasAudio, download = { process("download") }, transcribe = { process("transcribe") }, cancel = { process("cancel") }, play = play)
            else -> item { StudyPanel(tab, data.materials, data.transcript.isNotEmpty(), processing.running || study.running, study, generate) }
        }
        item { GlassButton("Удалить лекцию", enabled = !(processing.running && processing.lectureId == lecture.id) && !(study.running && study.lectureId == lecture.id)) { deleting = true } }
    }
    if (exporting) ExportDialog(lecture, data.transcript, data.materials) { exporting = false }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Удалить лекцию?") }, text = { Text("Аудио, текст и заметки будут удалены без возможности восстановления.") },
        confirmButton = { TextButton({ player?.release(); player = null; deleting = false; vm.delete(lecture) }) { Text("Удалить") } }, dismissButton = { TextButton({ deleting = false }) { Text("Отмена") } })
}
