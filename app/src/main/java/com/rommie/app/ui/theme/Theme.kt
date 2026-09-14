package com.rommie.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Mint,
    onPrimary = Color.White,
    primaryContainer = Lime,
    onPrimaryContainer = Ink,
    secondary = Coral,
    onSecondary = Color.White,
    tertiary = Amber,
    background = Canvas,
    surface = Paper,
    onBackground = Ink,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEF2EA),
    onSurfaceVariant = InkMuted,
    outline = Line,
)

@Composable
fun RommieTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}