package ru.lectograph.app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

@Composable fun BottomNavigation(selected: String, navigate: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(38.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f), tonalElevation = 6.dp, shadowElevation = 12.dp) {
        Row(Modifier.padding(7.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("library" to "Предметы", "drafts" to "Черновики", "record" to "Запись", "settings" to "Настройки").forEach { (key, title) ->
                val active = selected == key
                FilledTonalButton({ navigate(key) }, Modifier.weight(1f).heightIn(min = 52.dp).semantics { this.selected = active }, shape = RoundedCornerShape(30.dp), contentPadding = PaddingValues(4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (active) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)) { Text(title, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}
