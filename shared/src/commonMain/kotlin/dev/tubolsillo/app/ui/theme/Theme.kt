package dev.tubolsillo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Constantes de color auxiliares (sin colisiones con la API).
private val Color_White = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
private val Color_Charcoal = androidx.compose.ui.graphics.Color(0xFF131410)
private val Color_WarmGrayLight = androidx.compose.ui.graphics.Color(0xFFECE9E1)
private val Color_WarmGrayDark = androidx.compose.ui.graphics.Color(0xFF26271F)
private val Color_HairlineLight = androidx.compose.ui.graphics.Color(0x141C1B18L)
private val Color_HairlineDark = androidx.compose.ui.graphics.Color(0x1AFFFFFFL)
private val Color_TerracottaTintLight = androidx.compose.ui.graphics.Color(0xFFF3DCD8)
private val Color_TerracottaInkLight = androidx.compose.ui.graphics.Color(0xFF5E1B16)
private val Color_TerracottaTintDark = androidx.compose.ui.graphics.Color(0xFF4A221E)
private val Color_TerracottaInkDark = androidx.compose.ui.graphics.Color(0xFFF3DCD8)

private val LightColors = lightColorScheme(
    primary = ForestLight,
    onPrimary = OnForestLight,
    primaryContainer = ForestContainerLight,
    onPrimaryContainer = OnForestContainerLight,
    secondary = SageLight,
    onSecondary = Color_White,
    secondaryContainer = ForestContainerLight,
    onSecondaryContainer = OnForestContainerLight,
    tertiary = GoldLight,
    onTertiary = Color_White,
    error = ClayLight,
    onError = Color_White,
    errorContainer = Color_TerracottaTintLight,
    onErrorContainer = Color_TerracottaInkLight,
    background = PaperLight,
    onBackground = InkLight,
    surface = PaperSurfaceLight,
    onSurface = InkLight,
    surfaceVariant = Color_WarmGrayLight,
    onSurfaceVariant = MutedLight,
    outline = Color_HairlineLight,
    outlineVariant = Color_HairlineLight,
)

private val DarkColors = darkColorScheme(
    primary = ForestDark,
    onPrimary = OnForestDark,
    primaryContainer = ForestContainerDark,
    onPrimaryContainer = OnForestContainerDark,
    secondary = SageDark,
    onSecondary = Color_Charcoal,
    tertiary = GoldDark,
    onTertiary = Color_Charcoal,
    error = ClayDark,
    onError = Color_Charcoal,
    errorContainer = Color_TerracottaTintDark,
    onErrorContainer = Color_TerracottaInkDark,
    background = CharcoalDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = Color_WarmGrayDark,
    onSurfaceVariant = MutedDark,
    outline = Color_HairlineDark,
    outlineVariant = Color_HairlineDark,
)

@Composable
fun TubolsilloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = TubolsilloTypography,
        content = content,
    )
}
