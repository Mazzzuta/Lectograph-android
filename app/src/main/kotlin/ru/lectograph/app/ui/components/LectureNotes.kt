package ru.lectograph.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import ru.lectograph.app.models.Lecture

@Composable fun LectureNotes(lecture: Lecture, save: (String) -> Unit) {
    var value by rememberSaveable(lecture.id) { mutableStateOf(lecture.notes) }
    Column {
        OutlinedTextField(value, { value = it; save(it) }, Modifier.fillMaxWidth(), label = { Text("Мои заметки") }, minLines = 5)
        Text("Заметки сохраняются автоматически", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
