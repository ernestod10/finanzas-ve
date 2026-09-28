package dev.tubolsillo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Mapeo de los colores semánticos al ColorScheme de Material 3.
 *
 * La app usa los nombres de Material porque Compose los expone así, pero los
 * valores siguen el modelo de iOS: fondo agrupado (background), superficie de
 * fila (surface), tinta (onSurface), tinta secundaria (onSurfaceVariant) y
 * separador (outline). El acento de marca vive en primary y nunca en el fondo.
 */
private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = BrandGreenSoft,
    onPrimaryContainer = BrandGreenInk,

    secondary = BrandGreenBright,
    onSecondary = BrandGreenInk,

    tertiary = AmberLight,
    onTertiary = Color.White,

    error = RedLight,
    onError = Color.White,
    errorContainer = RedFillLight,
    onErrorContainer = Color(0xFF5E1B16),

    background = GroupedBackgroundLight,
    onBackground = LabelLight,

    surface = SurfaceLight,
    onSurface = LabelLight,

    surfaceVariant = FillLight,
    onSurfaceVariant = SecondaryLabelLight,

    outline = SeparatorLight,
    outlineVariant = OpaqueSeparatorLight,

    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = GroupedBackgroundLight,
    surfaceContainerHigh = FillLight,
    surfaceContainerHighest = FillLight,
)

private val DarkColors = darkColorScheme(
    primary = BrandGreenBright,
    onPrimary = BrandGreenInk,
    primaryContainer = BrandGreenDeep,
    onPrimaryContainer = BrandGreenSoft,

    secondary = BrandGreenBright,
    onSecondary = BrandGreenInk,

    tertiary = AmberDark,
    onTertiary = BrandGreenInk,

    error = RedDark,
    onError = Color(0xFF3A1A16),
    errorContainer = RedFillDark,
    onErrorContainer = Color(0xFFFFDAD4),

    background = GroupedBackgroundDark,
    onBackground = LabelDark,

    surface = SurfaceDark,
    onSurface = LabelDark,

    surfaceVariant = FillDark,
    onSurfaceVariant = SecondaryLabelDark,

    outline = SeparatorDark,
    outlineVariant = SeparatorDark,

    surfaceContainerLowest = GroupedBackgroundDark,
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = FillDark,
    surfaceContainerHighest = FillDark,
)

/** Colores que los componentes leen directamente (no existen en ColorScheme). */
val TubolsilloColors
    @Composable get() = LocalTubolsilloColors.current

data class TubolsilloExtraColors(
    val tertiaryLabel: Color,
    val separator: Color,
    val groupedBackground: Color,
)

private val LocalTubolsilloColors = androidx.compose.runtime.staticCompositionLocalOf {
    TubolsilloExtraColors(
        tertiaryLabel = TertiaryLabelLight,
        separator = SeparatorLight,
        groupedBackground = GroupedBackgroundLight,
    )
}

@Composable
fun TubolsilloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extra = if (darkTheme) {
        TubolsilloExtraColors(
            tertiaryLabel = TertiaryLabelDark,
            separator = SeparatorDark,
            groupedBackground = GroupedBackgroundDark,
        )
    } else {
        TubolsilloExtraColors(
            tertiaryLabel = TertiaryLabelLight,
            separator = SeparatorLight,
            groupedBackground = GroupedBackgroundLight,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalTubolsilloColors provides extra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = TubolsilloTypography,
            content = content,
        )
    }
}
