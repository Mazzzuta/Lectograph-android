package ru.lectograph.app.ui.lecture

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import ru.lectograph.app.database.LectureStore
import ru.lectograph.app.models.TranscriptSegment
import ru.lectograph.app.transcription.ProcessingStatus
import ru.lectograph.app.ui.components.*
import ru.lectograph.app.utils.LectureFormat

fun LazyListScope.transcriptPanel(segments: List<TranscriptSegment>, job: LectureStore.Job, state: ProcessingStatus,
    modelReady: Boolean, aiBusy: Boolean, showSegments: Boolean, toggleSegments: () -> Unit,
    download: () -> Unit, transcribe: () -> Unit, cancel: () -> Unit, play: (Long) -> Unit) {
    item { Heading("Полный текст") }
    if (state.running) item { GlassCard {
        Text("${state.message} · ${state.progress}%")
        LinearProgressIndicator(progress = { state.progress / 100f }, Modifier.fillMaxWidth())
        GlassButton("Отменить обработку", onClick = cancel)
    } }
    else if (job.error.isNotEmpty()) item { Text(job.error, color = MaterialTheme.colorScheme.error) }
    else if (state.message.isNotEmpty()) item { Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (!modelReady) {
        item { Text("Один раз скачайте Whisper base — 148 МБ. Затем аудио распознаётся на телефоне без интернета. Язык — русский.") }
        item { GlassButton("Скачать Whisper base · 148 МБ", !state.running && !aiBusy, primary = true, onClick = download) }
    } else item { GlassButton(if (job.status == "done") "Распознать повторно" else "Распознать запись", !state.running && !aiBusy, primary = true, onClick = transcribe) }
    if (job.status == "done" && segments.isEmpty()) item { Text("Речь в записи не найдена.") }
    if (segments.isNotEmpty()) {
        item { Text("Текст распознан автоматически. Для сверки с аудио откройте фрагменты с таймингами.", style = MaterialTheme.typography.bodySmall) }
        item { GlassButton(if (showSegments) "Показать цельный текст" else "Показать фрагменты с таймингами", onClick = toggleSegments) }
        if (showSegments) items(segments) { segment -> GlassCard {
            TextButton({ play(segment.startMs) }) { Text(LectureFormat.time(segment.startMs)) }
            SelectionContainer { Text(segment.text) }
        } }
        else item { GlassCard {
            SelectionContainer { Text(segments.joinToString(" ") { it.text.trim() }) }
        } }
    }
}
