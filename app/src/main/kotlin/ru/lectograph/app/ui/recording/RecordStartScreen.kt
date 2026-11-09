package ru.lectograph.app.ui.recording

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import ru.lectograph.app.R
import java.text.Collator
import java.util.Locale

@Composable
fun RecordStartScreen(subjects: List<String>, addSubject: (String) -> Unit, record: (String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val name = query.trim()
    val selectedSubject = subjects.firstOrNull { it.equals(name, ignoreCase = true) }
    val sorted = remember(subjects) { subjects.sortedWith(Collator.getInstance(Locale.forLanguageTag("ru"))) }
    val matches = remember(sorted, name) { sorted.filter { it.contains(name, ignoreCase = true) } }
    val destination = selectedSubject.orEmpty().takeIf { title.isNotBlank() }.orEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val top = (maxHeight * 0.40f - 58.dp).coerceAtLeast(16.dp)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(top))
            FilledIconButton(onClick = {
                expanded = false
                focus.clearFocus()
                record(title.trim(), destination)
            }, modifier = Modifier.size(116.dp), shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Icon(painterResource(R.drawable.nav_record), "Начать запись", Modifier.size(46.dp))
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Название") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(query, { query = it; expanded = true },
                        Modifier.fillMaxWidth().onFocusChanged { expanded = it.isFocused },
                        label = { Text("Предмет") }, placeholder = { Text("Поиск предмета") }, singleLine = true,
                        trailingIcon = { IconButton({ expanded = !expanded }) {
                            Text(if (expanded) "▴" else "▾", style = MaterialTheme.typography.titleMedium)
                        } })
                    DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = 256.dp),
                        properties = PopupProperties(focusable = false)) {
                        if (matches.isEmpty()) {
                            DropdownMenuItem(text = { Text("Предметы не найдены") }, onClick = {}, enabled = false)
                        }
                        matches.forEach { subject ->
                            DropdownMenuItem(text = { Text(subject, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                                modifier = Modifier.height(48.dp), onClick = {
                                    query = subject
                                    expanded = false
                                    focus.clearFocus()
                                })
                        }
                    }
                }
                IconButton(onClick = {
                    addSubject(name)
                    query = name
                    expanded = false
                    focus.clearFocus()
                }, enabled = name.isNotEmpty() && selectedSubject == null) {
                    Icon(painterResource(R.drawable.nav_add), "Добавить предмет")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(if (destination.isEmpty()) "Запись сохранится в черновиках" else "Запись сохранится в предмете «$destination»",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
