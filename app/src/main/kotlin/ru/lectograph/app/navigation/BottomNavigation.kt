package ru.lectograph.app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.lectograph.app.R
import ru.lectograph.app.ui.components.GlassSurface

@Composable fun BottomNavigation(selected: String, navigate: (String) -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), radius = 28.dp) {
        Row(Modifier.fillMaxWidth().selectableGroup().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Triple("record", "Запись", R.drawable.nav_record), Triple("library", "Предметы", R.drawable.nav_book),
                Triple("drafts", "Черновики", R.drawable.nav_drafts), Triple("settings", "Настройки", R.drawable.nav_settings)).forEach { (key, title, icon) ->
                val active = selected == key
                val ink = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                Box(Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                    .selectable(selected = active, role = Role.Tab, onClick = { navigate(key) }), contentAlignment = Alignment.Center) {
                    if (active) GlassSurface(Modifier.matchParentSize(), radius = 22.dp) { }
                    Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 2.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
                        Icon(painterResource(icon), contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
                        Text(title, color = ink, style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
