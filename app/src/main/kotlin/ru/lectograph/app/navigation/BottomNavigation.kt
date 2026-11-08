package ru.lectograph.app.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
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
        BoxWithConstraints(Modifier.fillMaxWidth().padding(6.dp)) {
        val index = listOf("record", "library", "drafts", "settings").indexOf(selected).coerceAtLeast(0)
        val cell = (maxWidth - 12.dp) / 4
        val position by animateFloatAsState(index.toFloat(), spring(dampingRatio = 0.78f, stiffness = 420f), label = "glassPosition")
        val stretch = (abs(index - position) * 0.10f).coerceAtMost(0.18f)
        val density = LocalDensity.current
        GlassSurface(Modifier.offset { IntOffset(with(density) { ((cell + 4.dp) * position).roundToPx() }, 0) }
            .width(cell).height(64.dp).graphicsLayer { scaleX = 1f + stretch; scaleY = 1f - stretch * 0.4f }, radius = 22.dp) { }
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Triple("record", "Запись", R.drawable.nav_record), Triple("library", "Предметы", R.drawable.nav_book),
                Triple("drafts", "Черновики", R.drawable.nav_drafts), Triple("settings", "Настройки", R.drawable.nav_settings)).forEach { (key, title, icon) ->
                val active = selected == key
                val ink = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(if (pressed) 0.91f else 1f, spring(stiffness = 600f), label = "tabPress")
                Box(Modifier.weight(1f).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(22.dp))
                    .selectable(selected = active, role = Role.Tab, interactionSource = interaction, indication = null, onClick = { navigate(key) }), contentAlignment = Alignment.Center) {
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
}
