package ru.lectograph.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import ru.lectograph.app.ui.components.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.lectograph.app.ai.StudyService
import ru.lectograph.app.navigation.BottomNavigation
import ru.lectograph.app.recording.RecordingService
import ru.lectograph.app.transcription.TranscriptionService
import ru.lectograph.app.ui.components.LectureDialog
import ru.lectograph.app.ui.lecture.LectureScreen
import ru.lectograph.app.ui.library.LibraryScreen
import ru.lectograph.app.ui.recording.RecordingScreen
import ru.lectograph.app.ui.settings.SettingsScreen
import ru.lectograph.app.ui.theme.LectographTheme
import ru.lectograph.app.viewmodels.LecturesViewModel

@Composable fun LectographApp(vm: LecturesViewModel, record: (String, String) -> Unit) {
    val context = LocalContext.current
    val data by vm.library.collectAsStateWithLifecycle()
    val section by vm.section.collectAsStateWithLifecycle()
    val subject by vm.subject.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val recording by RecordingService.state.collectAsStateWithLifecycle()
    val processing by TranscriptionService.state.collectAsStateWithLifecycle()
    val study by StudyService.state.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf("") }
    val lecture = data.lectures.firstOrNull { it.id == selected }
    var dockHeight by remember { mutableIntStateOf(0) }
    val dockPadding = with(LocalDensity.current) { dockHeight.toDp() }
    LaunchedEffect(data.error, recording.error) { (data.error ?: recording.error)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }
    fun service(intent: Intent, foreground: Boolean = true) {
        try { if (foreground) context.startForegroundService(intent) else context.startService(intent) }
        catch (_: RuntimeException) { Toast.makeText(context, "Не удалось запустить обработку", Toast.LENGTH_LONG).show() }
    }
    BackHandler(enabled = selected != -1L || subject.isNotEmpty()) { if (selected != -1L) vm.closeLecture() else vm.openSubject("") }
    LectographTheme(theme) {
        val background = MaterialTheme.colorScheme.background
        Surface(color = background) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(background, MaterialTheme.colorScheme.surface, background))).safeDrawingPadding().imePadding()) {
                Box(Modifier.fillMaxSize().padding(bottom = dockPadding)) {
                when {
                    section == "record" && recording.id != -1L -> RecordingScreen(data.lectures.firstOrNull { it.id == recording.id }, recording,
                        control = { service(Intent(context, RecordingService::class.java).setAction(it), false) }, notes = { vm.notes(recording.id, it) })
                    lecture != null -> LectureScreen(lecture, data, tab, processing, study, vm, edit = { dialog = "edit" },
                        process = { action -> service(Intent(context, TranscriptionService::class.java).setAction(action).putExtra("lecture", lecture.id), action != "cancel") },
                        generate = { action, question -> service(Intent(context, StudyService::class.java).putExtra("action", action).putExtra("lecture", lecture.id).putExtra("question", question)) })
                    section == "record" -> Page {
                        item { Heading("Запись", "Новая лекция или быстрая запись в черновики") }
                        item { GlassCard {
                            Text("Диктофон", style = MaterialTheme.typography.titleLarge)
                            Text("Запись продолжится при выключенном экране.")
                            GlassButton("● Начать запись", primary = true) { record("", "") }
                            TextButton({ dialog = "record" }) { Text("Указать название и предмет") }
                        } }
                    }
                    section == "settings" -> SettingsScreen(theme, vm::theme)
                    else -> LibraryScreen(data, section == "drafts", subject, vm, { dialog = "subject" }, { dialog = "renameSubject" })
                }
                }
                Box(Modifier.align(Alignment.BottomCenter).onSizeChanged { dockHeight = it.height }.padding(horizontal = 22.dp, vertical = 16.dp)) { BottomNavigation(section, vm::navigate) }
            }
        }
        if (dialog.isNotEmpty()) LectureDialog(dialog, if (dialog == "renameSubject") subject else if (dialog == "edit") lecture?.title.orEmpty() else "", if (dialog == "edit") lecture?.subject.orEmpty() else "", data.subjects,
            dismiss = { dialog = "" }, submit = { title, name -> when (dialog) {
                "record" -> record(title, name)
                "subject" -> vm.addSubject(title)
                "renameSubject" -> vm.renameSubject(subject, title)
                "edit" -> lecture?.let { vm.edit(it.id, title, name) }
            } })
    }
}
