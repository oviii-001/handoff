package com.ovi.handoff.mobile.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

val DarkColorScheme = darkColorScheme(
    primary = M3PrimaryDark,
    onPrimary = M3OnPrimaryDark,
    primaryContainer = M3PrimaryContainerDark,
    onPrimaryContainer = M3OnPrimaryContainerDark,

    secondary = M3SecondaryDark,
    onSecondary = M3OnSecondaryDark,
    secondaryContainer = M3SecondaryContainerDark,
    onSecondaryContainer = M3OnSecondaryContainerDark,

    tertiary = M3TertiaryDark,
    onTertiary = M3OnTertiaryDark,
    tertiaryContainer = M3TertiaryContainerDark,
    onTertiaryContainer = M3OnTertiaryContainerDark,

    error = M3ErrorDark,
    onError = M3OnErrorDark,
    errorContainer = M3ErrorContainerDark,
    onErrorContainer = M3OnErrorContainerDark,

    surface = M3SurfaceDark,
    onSurface = M3OnSurfaceDark,
    surfaceVariant = M3SurfaceHighDark,
    onSurfaceVariant = M3OnSurfaceVariantDark,

    surfaceContainerLowest = M3SurfaceLowestDark,
    surfaceContainerLow = M3SurfaceLowDark,
    surfaceContainer = M3SurfaceDark,
    surfaceContainerHigh = M3SurfaceHighDark,
    surfaceContainerHighest = M3SurfaceHighestDark,
    surfaceBright = M3SurfaceBrightDark,
    surfaceDim = M3SurfaceDimDark,

    outline = M3OutlineDark,
    outlineVariant = M3OutlineVariantDark
)

val LightColorScheme = lightColorScheme(
    primary = M3PrimaryContainerDark,
    onPrimary = M3PrimaryDark,
    primaryContainer = M3OnPrimaryContainerDark,
    onPrimaryContainer = M3OnPrimaryDark,

    secondary = M3SecondaryContainerDark,
    onSecondary = M3SecondaryDark,
    secondaryContainer = M3OnSecondaryContainerDark,
    onSecondaryContainer = M3OnSecondaryDark,

    tertiary = M3TertiaryContainerDark,
    onTertiary = M3TertiaryDark,
    tertiaryContainer = M3OnTertiaryContainerDark,
    onTertiaryContainer = M3OnTertiaryDark
)

@Composable
public fun HandoffTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Official M3 Expressive guideline: enable dynamic color on Android 12+
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HandoffTypography,
        shapes = HandoffShapes,
        content = content
    )
}
