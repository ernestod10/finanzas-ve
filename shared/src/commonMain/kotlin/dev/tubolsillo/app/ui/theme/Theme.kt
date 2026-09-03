package dev.tubolsillo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = SurfaceLight,
    primaryContainer = Green90,
    onPrimaryContainer = Green20,
    secondary = Teal40,
    onSecondary = SurfaceLight,
    secondaryContainer = Teal80,
    onSecondaryContainer = Teal20,
    tertiary = Amber40,
    onTertiary = Green20,
    error = Red40,
    onError = SurfaceLight,
    background = NeutralLight,
    onBackground = NeutralDark,
    surface = SurfaceLight,
    onSurface = NeutralDark,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = Color_OnSurfaceVariantLight,
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green20,
    onPrimaryContainer = Green90,
    secondary = Teal80,
    onSecondary = Teal20,
    secondaryContainer = Teal20,
    onSecondaryContainer = Teal80,
    tertiary = Amber80,
    onTertiary = Green20,
    error = Red80,
    onError = Green20,
    background = NeutralDark,
    onBackground = SurfaceVariantLight,
    surface = SurfaceDark,
    onSurface = SurfaceVariantLight,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color_OnSurfaceVariantDark,
)

@Composable
fun TubolsilloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = FinanzaTypography,
        content = content,
    )
}
