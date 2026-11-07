package ru.lectograph.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import ru.lectograph.app.ui.components.*
import ru.lectograph.app.utils.LectureFormat
import ru.lectograph.app.viewmodels.LibraryState
import ru.lectograph.app.viewmodels.LecturesViewModel

@Composable fun LibraryScreen(data: LibraryState, drafts: Boolean, subject: String, vm: LecturesViewModel,
    quickRecord: () -> Unit, newRecord: () -> Unit, newSubject: () -> Unit) {
    var query by rememberSaveable(drafts, subject) { mutableStateOf("") }
    val lectures = data.lectures.filter { drafts == it.subject.isEmpty() && (subject.isEmpty() || subject == it.subject) &&
        (it.title + " " + it.notes).contains(query, ignoreCase = true) }
    Page {
        item { Heading(if (drafts) "Черновики" else subject.ifEmpty { "Твои лекции" }, "Записывай. Разбирайся. Запоминай.") }
        item { GlassButton("●  Быстрая запись", primary = true, onClick = quickRecord) }
        item { GlassButton("Запись с названием и предметом", onClick = newRecord) }
        if (!drafts && subject.isEmpty()) {
            item { Text("Предметы", style = MaterialTheme.typography.titleLarge) }
            items(data.subjects, key = { "subject:$it" }) { name -> GlassButton(name) { vm.openSubject(name) } }
            item { GlassButton("+ Создать предмет", onClick = newSubject) }
        } else if (subject.isNotEmpty()) item { GlassButton("← Все предметы") { vm.openSubject("") } }
        item { Text(if (drafts) "Без предмета" else "Последние записи", style = MaterialTheme.typography.titleLarge) }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Поиск по названию и заметкам") }, singleLine = true) }
        if (lectures.isEmpty()) item { Text("Пока нет лекций. Начни с записи.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(lectures, key = { it.id }) { lecture ->
            GlassCard(Modifier.clickable { vm.openLecture(lecture.id) }) {
                Text(lecture.title, style = MaterialTheme.typography.titleLarge)
                Text("${lecture.subject.ifEmpty { "Черновик" }} · ${LectureFormat.date(lecture.created)} · ${LectureFormat.time(lecture.duration)}", style = MaterialTheme.typography.bodySmall)
                Text(LectureFormat.status(lecture.status), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
