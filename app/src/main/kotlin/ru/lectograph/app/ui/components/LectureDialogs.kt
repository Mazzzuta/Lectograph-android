package ru.lectograph.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun LectureDialog(mode: String, initialTitle: String, initialSubject: String, subjects: List<String>,
    dismiss: () -> Unit, submit: (String, String) -> Unit) {
    var title by rememberSaveable(mode) { mutableStateOf(initialTitle) }
    var subject by rememberSaveable(mode) { mutableStateOf(initialSubject) }
    var invalid by rememberSaveable(mode) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(when (mode) { "record" -> "Новая запись"; "subject" -> "Новый предмет"; else -> "Оформить лекцию" }) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text(if (mode == "subject") "Название предмета" else "Название лекции") }, isError = invalid && title.isBlank(), singleLine = true)
            if (mode != "subject") {
                OutlinedTextField(subject, { subject = it }, label = { Text("Предмет (пусто — черновик)") }, isError = invalid && mode == "record" && subject.isBlank(), singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    subjects.forEach { name -> SuggestionChip({ subject = name }, label = { Text(name) }) }
                }
            }
            if (invalid) Text("Укажите название${if (mode == "record") " и предмет" else ""}", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton({
        invalid = title.isBlank() || mode == "record" && subject.isBlank()
        if (!invalid) { submit(title.trim(), subject.trim()); dismiss() }
    }) { Text(if (mode == "record") "Начать" else "Сохранить") } }, dismissButton = { TextButton(dismiss) { Text("Отмена") } })
}
