package ru.lectograph.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun LectographTheme(choice: String, content: @Composable () -> Unit) {
    val dark = choice == "dark" || choice == "system" && isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFFF5F6F7), onPrimary = Color(0xFF17191C), background = Color(0xFF101214),
        surface = Color(0xFF23262A), onSurface = Color(0xFFFAFAFA), onSurfaceVariant = Color(0xFFC1C4C9)
    ) else lightColorScheme(primary = Color(0xFF272A30), onPrimary = Color.White, background = Color(0xFFF5F0E5), surface = Color(0xFFE8E1D4))
    MaterialTheme(colorScheme = scheme, content = content)
}
