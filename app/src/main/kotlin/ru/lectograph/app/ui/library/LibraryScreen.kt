package ru.lectograph.app.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.lectograph.app.ui.components.*
import ru.lectograph.app.R
import androidx.compose.ui.res.painterResource
import ru.lectograph.app.utils.LectureFormat
import ru.lectograph.app.viewmodels.LibraryState
import ru.lectograph.app.viewmodels.LecturesViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable fun LibraryScreen(data: LibraryState, drafts: Boolean, subject: String, vm: LecturesViewModel,
    newSubject: () -> Unit, editSubject: () -> Unit) {
    var query by rememberSaveable(drafts, subject) { mutableStateOf("") }
    var deleting by rememberSaveable { mutableStateOf("") }
    val listSubjects = !drafts && subject.isEmpty()
    val lectures = data.lectures.filter { (if (drafts) it.subject.isEmpty() else it.subject == subject) &&
        (it.title + " " + it.notes).contains(query, ignoreCase = true) }
    Page {
        if (subject.isNotEmpty()) item { TextButton({ vm.openSubject("") }) { Text("← Предметы") } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Heading(if (drafts) "Черновики" else subject.ifEmpty { "Предметы" }) }
            if (listSubjects) IconButton(newSubject, Modifier.semantics { contentDescription = "Добавить предмет" }) { Icon(painterResource(R.drawable.nav_add), null) }
            else if (!drafts) IconButton(editSubject, Modifier.semantics { contentDescription = "Редактировать предмет" }) { Icon(painterResource(R.drawable.nav_edit), null) }
        } }
        if (listSubjects) {
            item { Text("${data.subjects.size} предметов · удерживайте предмет для удаления", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (data.subjects.isEmpty()) item { Text("Добавьте первый предмет кнопкой +") }
            items(data.subjects, key = { "subject:$it" }) { name ->
                GlassCard(Modifier.combinedClickable(onClick = { vm.openSubject(name) }, onLongClickLabel = "Удалить предмет", onLongClick = { deleting = name })) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text("Лекций: ${data.lectures.count { it.subject == name }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            item { Text(if (drafts) "Записи без предмета" else "Лекции · ${lectures.size}", style = MaterialTheme.typography.titleMedium) }
            item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Поиск по названию и заметкам") }, singleLine = true) }
            if (lectures.isEmpty()) item { Text("Лекций пока нет. Новую запись можно начать во вкладке «Запись».", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(lectures, key = { it.id }) { lecture ->
                GlassCard(Modifier.clickable { vm.openLecture(lecture.id) }) {
                    Text(lecture.title, style = MaterialTheme.typography.titleMedium)
                    Text("${LectureFormat.date(lecture.created)} · ${LectureFormat.time(lecture.duration)}", style = MaterialTheme.typography.bodySmall)
                    Text(LectureFormat.status(lecture.status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (deleting.isNotEmpty()) AlertDialog(onDismissRequest = { deleting = "" }, title = { Text("Удалить предмет «$deleting»?") },
        text = { Text("Лекции, аудио и заметки сохранятся в черновиках.") },
        confirmButton = { TextButton({ vm.deleteSubject(deleting); deleting = "" }) { Text("Удалить предмет") } },
        dismissButton = { TextButton({ deleting = "" }) { Text("Отмена") } })
}
