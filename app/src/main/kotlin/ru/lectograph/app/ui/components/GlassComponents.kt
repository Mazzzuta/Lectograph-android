package ru.lectograph.app.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun Page(content: LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, top = 20.dp, end = 22.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
@Composable fun Heading(title: String, subtitle: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle.isNotEmpty()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun GlassSurface(modifier: Modifier = Modifier, radius: Dp = 24.dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(radius)
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.red < 0.5f
    val rim = if (dark) Color.White else Color(0xFF706A60)
    // Layered optical highlights keep the effect available on Android 8+, without a blur of the text.
    Box(modifier.shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
        .clip(shape).background(scheme.surface.copy(alpha = if (dark) 0.58f else 0.72f))
        .drawWithCache {
            val sheen = Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) 0.08f else 0.25f), Color.White.copy(alpha = 0.015f), rim.copy(alpha = 0.06f)),
                start = Offset.Zero, end = Offset(size.width * 0.8f, size.height))
            val glow = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                center = Offset(size.width * 0.9f, size.height * 1.25f), radius = size.width * 0.8f)
            onDrawBehind { drawRect(sheen); drawRect(glow) }
        }.liquidLayer(radius, true).border(BorderStroke(1.dp, Brush.linearGradient(listOf(rim.copy(alpha = 0.18f), rim.copy(alpha = 0.04f), rim.copy(alpha = 0.10f)))), shape)) {
        content()
    }
}

@Composable fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable fun GlassBackdrop() {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.red < 0.5f
    Box(Modifier.fillMaxSize().background(scheme.background).drawWithCache {
        val upper = Brush.radialGradient(listOf(scheme.onSurface.copy(alpha = if (dark) 0.15f else 0.07f), Color.Transparent),
            center = Offset(size.width * 1.05f, size.height * 0.12f), radius = size.width * 1.1f)
        val lower = Brush.radialGradient(listOf(Color(0xFF40C9CB).copy(alpha = if (dark) 0.30f else 0.12f), Color.Transparent),
            center = Offset(-size.width * 0.3f, size.height * 0.75f), radius = size.width * 1.2f)
        onDrawBehind { drawRect(upper); drawRect(lower) }
    }.liquidLayer(0.dp, false))
}

@Composable fun GlassButton(text: String, enabled: Boolean = true, primary: Boolean = false, onClick: () -> Unit) {
    if (!primary) {
        OutlinedButton(onClick, Modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f))) { Text(text) }
        return
    }
    Button(onClick, Modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text(text) }
}
