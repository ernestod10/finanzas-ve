package dev.tubolsillo.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Escala tipográfica de iOS (Dynamic Type, tamaño por defecto "Large"),
 * medida en `typography.md › Specifications` de las HIG.
 *
 * Cambio de fondo frente a la escala anterior: los títulos van en **Regular**
 * y el énfasis se logra con peso puntual (Bold en el titular, Semibold en
 * Headline), no con ExtraBold en todo. El cuerpo sube a **17sp**, que es el
 * tamaño de lectura por defecto de iOS y antes no existía.
 *
 * El tracking negativo fuerte (−0.8) se retira: iOS ajusta el tracking por
 * tamaño de forma sutil, no editorial.
 */
val TubolsilloTypography = Typography(
    // Cifra protagonista del rediseño (saldo, total del período).
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 44.sp,
        letterSpacing = -0.6.sp,
    ),
    // Large Title 34 / Regular (Bold para el énfasis del titular).
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = 0.37.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.36.sp,
    ),
    // Title 1 · 28 / Regular.
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.36.sp,
    ),
    // Title 2 · 22 / Regular.
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = -0.26.sp,
    ),
    // Title 3 · 20 / Regular (título en línea de la barra de navegación).
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = -0.41.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = -0.26.sp,
    ),
    // Headline · 17 / Semibold.
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = -0.41.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = -0.24.sp,
    ),
    // Body · 17 / Regular: el tamaño de lectura de iOS.
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = -0.41.sp,
    ),
    // Subhead · 15 / Regular.
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = -0.24.sp,
    ),
    // Footnote · 13 / Regular.
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = -0.08.sp,
    ),
    // Botones: en iOS el texto de un botón es 17 / Regular.
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = -0.41.sp,
    ),
    // Subhead · 15.
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = -0.24.sp,
    ),
    // Footnote · 13: también es el encabezado de sección de las listas
    // agrupadas de iOS (versalitas + tracking), no un caption diminuto.
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = -0.08.sp,
    ),
)

/** Números tabulares: alineación de columnas de montos en toda la app. */
const val TNUM = "tnum"

/** Estilo base para montos. */
val moneyTextStyle = TextStyle(fontFeatureSettings = TNUM)
