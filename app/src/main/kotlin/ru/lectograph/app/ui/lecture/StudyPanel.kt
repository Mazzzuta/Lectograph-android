package ru.lectograph.app.ui.lecture

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import ru.lectograph.app.ai.GeminiClient
import ru.lectograph.app.ai.StudyStatus
import ru.lectograph.app.ui.components.*

@Composable fun StudyPanel(tab: String, materials: Map<String, String>, hasText: Boolean, busy: Boolean, state: StudyStatus,
    generate: (String, String) -> Unit) {
    val isTest = tab == "test"
    val saved = materials[if (isTest) "test" else "study"].orEmpty()
    val data = remember(saved) { runCatching { JSONObject(saved) }.getOrNull() }
    var consent by rememberSaveable { mutableStateOf(false) }
    val available = GeminiClient.configured() && !busy && hasText
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!GeminiClient.configured()) Text("Сервер обработки пока не подключён. Распознавание и сохранённые материалы доступны без него.")
        else if (state.message.isNotEmpty()) Text(state.message)
        GlassButton(if (isTest) "Создать тест · до 10 вопросов" else "Создать конспекты и разбор", available, primary = true) { consent = true }
        Text(if (isTest) "До 3 новых тестов в сутки. Готовый тест можно проходить без ограничений." else "До 3 обработок лекций в сутки. Открытие готовых материалов не расходует запросы.", style = MaterialTheme.typography.bodySmall)
        if (data == null) Text("Готовых материалов пока нет. Сначала распознайте запись.")
        else if (isTest) Quiz(data.optJSONArray("questions") ?: JSONArray(), saved)
        else if (tab == "summary") {
            GlassCard { Text("Кратко", style = MaterialTheme.typography.titleLarge); SelectionContainer { Text(data.optString("short")) } }
            GlassCard { Text("Подробный конспект", style = MaterialTheme.typography.titleLarge); SelectionContainer { Text(data.optString("detailed")) } }
        } else {
            Text("Уточнения · добавлено ИИ", style = MaterialTheme.typography.titleLarge)
            val clarifications = data.optJSONArray("clarifications") ?: JSONArray()
            if (clarifications.length() == 0) Text("Уточнений не найдено.")
            for (i in 0 until clarifications.length()) clarifications.optJSONObject(i)?.let { item ->
                GlassCard { Text(item.optString("fragment"), style = MaterialTheme.typography.titleMedium); Text(item.optString("explanation")) }
            }
            Text("Вопросы из записи", style = MaterialTheme.typography.titleLarge)
            val questions = data.optJSONArray("studentQuestions") ?: JSONArray()
            if (questions.length() == 0) Text("Вопросов студентов не найдено.")
            for (i in 0 until questions.length()) questions.optJSONObject(i)?.let { item -> GlassCard {
                Text(item.optString("question"), style = MaterialTheme.typography.titleMedium)
                val teacher = item.optString("teacherAnswer")
                Text(if (teacher.isEmpty()) "Ответ преподавателя в записи не найден." else "Ответ преподавателя: $teacher")
                if (item.optString("aiAnswer").isNotEmpty()) Text("Пояснение ИИ по лекции: ${item.optString("aiAnswer")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            var question by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(question, { question = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text("Свой вопрос по лекции · до 1000 символов") })
            GlassButton("Задать вопрос · до 10 в сутки", available && question.isNotBlank()) { generate("question", question) }
            val answer = remember(materials["question"]) { runCatching { JSONObject(materials["question"].orEmpty()).getString("answer") }.getOrDefault("") }
            if (answer.isNotEmpty()) GlassCard { Text("Ответ ИИ по лекции"); Text(answer) }
            Text("Интернет-поиск пока не подключён. Пояснения строятся только по лекции.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Обработать через Gemini?") },
        text = { Text("Текст этой лекции будет отправлен на сервер и в Gemini. Аудио останется на телефоне. На бесплатном уровне Gemini данные могут использоваться для улучшения сервисов Google.") },
        confirmButton = { TextButton({ consent = false; generate(if (isTest) "test" else "study", "") }) { Text("Отправить текст") } },
        dismissButton = { TextButton({ consent = false }) { Text("Отмена") } })
}

@Composable private fun Quiz(questions: JSONArray, key: String) {
    if (questions.length() == 0) { Text("Недостаточно материала для вопросов."); return }
    val valid = (0 until questions.length()).all {
        val q = questions.optJSONObject(it)
        q != null && q.optJSONArray("options")?.length() == 4 && q.optInt("correctIndex", -1) in 0..3
    }
    if (!valid) { Text("Не удалось открыть тест. Создайте его повторно."); return }
    var answers by rememberSaveable(key) { mutableStateOf(IntArray(questions.length()) { -1 }) }
    var checked by rememberSaveable(key) { mutableStateOf(false) }
    for (i in 0 until questions.length()) {
        val question = questions.getJSONObject(i)
        GlassCard {
            Text("${i + 1}. ${question.optString("question")}", style = MaterialTheme.typography.titleMedium)
            val options = question.getJSONArray("options")
            repeat(4) { index ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(answers[i] == index, role = Role.RadioButton) {
                    answers = answers.copyOf().also { it[i] = index }; checked = false
                }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(answers[i] == index, onClick = null); Text(options.getString(index))
                }
            }
            if (checked) Text((if (answers[i] == question.getInt("correctIndex")) "Верно. " else "Ошибка. ") +
                question.optString("explanation") + "\nИз лекции: «${question.optString("sourceQuote")}»")
        }
    }
    GlassButton("Проверить ответы", answers.none { it == -1 }, primary = true) { checked = true }
    if (checked) Text("Результат: ${(0 until questions.length()).count { answers[it] == questions.getJSONObject(it).getInt("correctIndex") }} из ${questions.length()}", style = MaterialTheme.typography.titleLarge)
}
