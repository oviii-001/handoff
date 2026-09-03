package com.ovi.handoff.mobile.core.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AntigravityViolet,
    onPrimary = TextPrimary,
    primaryContainer = AntigravityVioletDark,
    onPrimaryContainer = AntigravityVioletLight,
    secondary = AntigravityVioletLight,
    onSecondary = DarkBg,
    background = DarkBg,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    error = RiskCritical,
    errorContainer = RiskCriticalContainer,
    onError = TextPrimary
)

@Composable
fun HandoffTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Keep developer palette consistent by default
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HandoffTypography,
        content = content
    )
}
