package com.indic.meshvoice.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    secondary = SaffronOrange,
    tertiary = NeonEmerald,
    background = DarkBg,
    surface = DarkSurface,
    onPrimary = DarkBg,
    onSecondary = DarkBg,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun IndicMeshVoiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
