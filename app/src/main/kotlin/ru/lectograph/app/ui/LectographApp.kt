package ru.lectograph.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import ru.lectograph.app.R
import androidx.activity.compose.BackHandler
import ru.lectograph.app.ui.components.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
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
    val swipeThreshold = with(LocalDensity.current) { (LocalConfiguration.current.screenWidthDp * 0.22f).dp.toPx() }
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
            Box(Modifier.fillMaxSize()) {
                GlassBackdrop()
                Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                AnimatedContent(
                    targetState = Triple(section, subject, selected),
                    modifier = Modifier.fillMaxSize().padding(bottom = dockPadding).clipToBounds()
                        .pointerInput(section, selected, subject, swipeThreshold) {
                            if (selected != -1L || subject.isNotEmpty()) return@pointerInput
                            var distance = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { distance = 0f },
                                onDragCancel = { distance = 0f },
                                onDragEnd = {
                                    val order = listOf("record", "library", "drafts", "settings")
                                    val step = when { distance < -swipeThreshold -> 1; distance > swipeThreshold -> -1; else -> 0 }
                                    val next = order.indexOf(section) + step
                                    if (step != 0 && next in order.indices) vm.navigate(order[next])
                                    distance = 0f
                                },
                                onHorizontalDrag = { change, amount -> change.consume(); distance += amount }
                            )
                        },
                    transitionSpec = {
                        val order = listOf("record", "library", "drafts", "settings")
                        val forward = if (initialState.first != targetState.first) order.indexOf(targetState.first) > order.indexOf(initialState.first)
                            else if (initialState.third != targetState.third) targetState.third != -1L
                            else targetState.second.isNotEmpty()
                        val direction = if (forward) 1 else -1
                        val easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
                        (slideInHorizontally(tween(220, easing = easing)) { it * direction } togetherWith
                            slideOutHorizontally(tween(220, easing = easing)) { -it * direction }).using(SizeTransform(clip = false))
                    }, label = "screenSwipe"
                ) { destination ->
                val section = destination.first
                val subject = destination.second
                val lecture = data.lectures.firstOrNull { it.id == destination.third }
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
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                FilledIconButton({ record("", "") }, Modifier.size(88.dp), shape = CircleShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                                    Icon(painterResource(R.drawable.nav_record), "Начать запись", Modifier.size(36.dp))
                                }
                            }
                            Text("Начать запись в черновик", style = MaterialTheme.typography.bodySmall)
                            TextButton({ dialog = "record" }) { Text("Указать название и предмет") }
                        } }
                    }
                    section == "settings" -> SettingsScreen(theme, vm::theme, vm::refresh)
                    else -> LibraryScreen(data, section == "drafts", subject, vm, { dialog = "subject" }, { dialog = "renameSubject" })
                }
                }
                Box(Modifier.align(Alignment.BottomCenter).onSizeChanged { dockHeight = it.height }.padding(horizontal = 22.dp, vertical = 16.dp)) { BottomNavigation(section, vm::navigate) }
            }
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
