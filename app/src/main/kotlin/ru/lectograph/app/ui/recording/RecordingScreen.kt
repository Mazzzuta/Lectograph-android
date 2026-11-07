package ru.lectograph.app.ui.recording

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import ru.lectograph.app.models.Lecture
import ru.lectograph.app.recording.RecordingService
import ru.lectograph.app.recording.RecordingStatus
import ru.lectograph.app.ui.components.*
import ru.lectograph.app.utils.LectureFormat

@Composable fun RecordingScreen(lecture: Lecture?, state: RecordingStatus, control: (String) -> Unit, notes: (String) -> Unit) {
    var elapsed by remember { mutableLongStateOf(RecordingService.elapsed()) }
    LaunchedEffect(state.id) { while (true) { elapsed = RecordingService.elapsed(); delay(500) } }
    Page {
        item { Heading(lecture?.title ?: "Запись лекции", "ДИКТОФОН") }
        item { GlassCard {
            Text(LectureFormat.time(elapsed), style = MaterialTheme.typography.displayLarge)
            Text(if (state.paused) "Запись на паузе" else "Запись продолжается при выключенном экране")
        } }
        item { GlassButton(if (state.paused) "Продолжить" else "Пауза") { control("pause") } }
        item { GlassButton("Завершить и сохранить", primary = true) { control("stop") } }
        if (lecture != null) item { LectureNotes(lecture, notes) }
    }
}
