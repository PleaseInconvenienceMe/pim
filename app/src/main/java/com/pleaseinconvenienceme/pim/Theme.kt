package com.pleaseinconvenienceme.pim

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006492),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8E6FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF4D6270),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E7F8),
    onSecondaryContainer = Color(0xFF081E28),
    surface = Color(0xFFF6FAFE),
    onSurface = Color(0xFF181C1E),
    surfaceVariant = Color(0xFFDCE4EA),
    onSurfaceVariant = Color(0xFF40484D),
    background = Color(0xFFF6FAFE),
    onBackground = Color(0xFF181C1E),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF70787D),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF0A7AB0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF004C6E),
    onPrimaryContainer = Color(0xFFC8E6FF),
    secondary = Color(0xFFB4CAD9),
    onSecondary = Color(0xFF1E333F),
    secondaryContainer = Color(0xFF354A56),
    onSecondaryContainer = Color(0xFFD0E7F8),
    surface = Color(0xFF212121),
    onSurface = Color(0xFFE1E3E5),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFC0C8CE),
    background = Color(0xFF212121),
    onBackground = Color(0xFFE1E3E5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF2E2010),  // Option C: warm dark amber
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8A9297),
    outlineVariant = Color(0xFF606060),
)

@Composable
fun PimTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme,
        content = content
    )
}
