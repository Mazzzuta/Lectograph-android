package ru.lectograph.app.ui.settings

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import ru.lectograph.app.BuildConfig
import ru.lectograph.app.ui.components.*

@Composable fun SettingsScreen(theme: String, choose: (String) -> Unit) {
    Page {
        item { Heading("Настройки") }
        item { Text("Цветовая схема", style = MaterialTheme.typography.titleLarge) }
        listOf("system" to "Как в системе", "dark" to "Графитовая", "light" to "Светлая · цвет кости").forEach { (key, title) ->
            item { GlassButton((if (theme == key) "✓  " else "") + title) { choose(key) } }
        }
        item { GlassCard {
            Text("Хранение", style = MaterialTheme.typography.titleLarge)
            Text("Записи и заметки хранятся на этом устройстве. Аудио распознаётся локально; для конспектов в Gemini отправляется только текст.")
            Text("Экспорт и импорт будут добавлены отдельно.")
        } }
        item { Text("Лектограф · ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
