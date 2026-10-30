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
private fun buildLightColors(accent: AppAccent) = lightColorScheme(
    primary = accent.primaryLight,
    onPrimary = Color.White,
    primaryContainer = accent.softContainerLight,
    onPrimaryContainer = accent.ink,

    secondary = accent.primaryDark,
    onSecondary = accent.ink,

    tertiary = AmberLight,
    onTertiary = Color.White,

    error = RedLight,
    onError = Color.White,
    errorContainer = RedFillLight,
    onErrorContainer = Color(0xFF5E1B16),

    background = CanvasLight,
    onBackground = LabelLight,

    surface = CardLight,
    onSurface = LabelLight,

    surfaceVariant = TileFillLight,
    onSurfaceVariant = SecondaryLabelLight,

    outline = SeparatorLight,
    outlineVariant = OpaqueSeparatorLight,

    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = GroupedBackgroundLight,
    surfaceContainerHigh = FillLight,
    surfaceContainerHighest = FillLight,
)

private fun buildDarkColors(accent: AppAccent) = darkColorScheme(
    primary = accent.primaryDark,
    onPrimary = accent.ink,
    primaryContainer = accent.deepContainerDark,
    onPrimaryContainer = accent.softContainerLight,

    secondary = accent.primaryDark,
    onSecondary = accent.ink,

    tertiary = AmberDark,
    onTertiary = accent.ink,

    error = RedDark,
    onError = Color(0xFF3A1A16),
    errorContainer = RedFillDark,
    onErrorContainer = Color(0xFFFFDAD4),

    background = CanvasDark,
    onBackground = LabelDark,

    surface = CardDark,
    onSurface = LabelDark,

    surfaceVariant = TileFillDark,
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
    /** Superficie de tarjeta del rediseño (blanco puro / casi negro). */
    val card: Color = CardLight,
    /** Lienzo detrás de las tarjetas. */
    val canvas: Color = CanvasLight,
    /** Relleno de teselas y campos sobre una tarjeta (tasas, chips). */
    val tileFill: Color = TileFillLight,
    val cardBorder: Color = CardBorderLight,
    /** Señales de resultado: delta de ingresos/gastos, tendencias. */
    val positive: Color = PositiveLight,
    val negative: Color = NegativeLight,
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
    accent: AppAccent = AppAccent.EMERALD,
    content: @Composable () -> Unit,
) {
    val extra = if (darkTheme) {
        TubolsilloExtraColors(
            tertiaryLabel = TertiaryLabelDark,
            separator = SeparatorDark,
            groupedBackground = GroupedBackgroundDark,
            card = CardDark,
            canvas = CanvasDark,
            tileFill = TileFillDark,
            cardBorder = CardBorderDark,
            positive = PositiveDark,
            negative = NegativeDark,
        )
    } else {
        TubolsilloExtraColors(
            tertiaryLabel = TertiaryLabelLight,
            separator = SeparatorLight,
            groupedBackground = GroupedBackgroundLight,
            card = CardLight,
            canvas = CanvasLight,
            tileFill = TileFillLight,
            cardBorder = CardBorderLight,
            positive = PositiveLight,
            negative = NegativeLight,
        )
    }
    val scheme = androidx.compose.runtime.remember(darkTheme, accent) {
        if (darkTheme) buildDarkColors(accent) else buildLightColors(accent)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalTubolsilloColors provides extra) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TubolsilloTypography,
            content = content,
        )
    }
}
