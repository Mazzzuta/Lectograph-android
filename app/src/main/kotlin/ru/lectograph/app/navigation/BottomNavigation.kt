package ru.lectograph.app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.lectograph.app.R
import ru.lectograph.app.ui.components.GlassSurface

@Composable fun BottomNavigation(selected: String, navigate: (String) -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), radius = 28.dp) {
        NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Transparent, windowInsets = WindowInsets(0, 0, 0, 0)) {
            listOf(Triple("record", "Запись", R.drawable.nav_record), Triple("library", "Предметы", R.drawable.nav_book),
                Triple("drafts", "Черновики", R.drawable.nav_drafts), Triple("settings", "Настройки", R.drawable.nav_settings)).forEach { (key, title, icon) ->
                NavigationBarItem(selected = selected == key, onClick = { navigate(key) },
                    icon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp)) },
                    label = { Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary, selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
    }
}
