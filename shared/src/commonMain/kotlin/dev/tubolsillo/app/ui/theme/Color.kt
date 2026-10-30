package dev.tubolsillo.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colores semánticos, nombrados por ROL y no por apariencia (regla de
 * `color.md › System colors` de las HIG): fondo agrupado, superficie, tinta,
 * tinta secundaria, separador y acento. Los componentes nunca eligen un color
 * por cómo se ve, sino por lo que significa.
 *
 * Base neutra de iOS con el verde de TuBolsillo como acento de marca: el verde
 * manda en acciones, cifras y estados, nunca en el ambiente.
 *
 * Nota de accesibilidad: el secondaryLabel real de iOS (rgba(60,60,67,.6))
 * queda en ~3.5:1 y no pasa WCAG AA. Aquí se usa un gris equivalente más
 * oscuro (#5A5A5E, 6.15:1 sobre el fondo agrupado) para cumplir el mínimo de
 * 4.5:1 sin perder la jerarquía.
 */

// ---------- Claro ----------
val GroupedBackgroundLight = Color(0xFFF2F2F7)   // fondo agrupado (lienzo)
val SurfaceLight = Color(0xFFFFFFFF)             // filas y tarjetas
val FillLight = Color(0xFFE5E5EA)                // relleno terciario (chips, campos)

val LabelLight = Color(0xFF000000)               // tinta primaria
val SecondaryLabelLight = Color(0xFF5A5A5E)      // tinta secundaria (6.15:1)
val TertiaryLabelLight = Color(0xFF8A8A8E)       // tinta terciaria (decorativa)
val SeparatorLight = Color(0xFFC6C6C8)           // separador (hairline)
val OpaqueSeparatorLight = Color(0xFFC6C6C8)

// ---------- Oscuro (negro pleno, como el agrupado de iOS) ----------
val GroupedBackgroundDark = Color(0xFF000000)
val SurfaceDark = Color(0xFF1C1C1E)
val FillDark = Color(0xFF2C2C2E)

val LabelDark = Color(0xFFFFFFFF)
val SecondaryLabelDark = Color(0xFFA0A0A5)       // 6.54:1 sobre superficie
val TertiaryLabelDark = Color(0xFF8A8A93)
val SeparatorDark = Color(0xFF38383A)

// ---------- Superficies del rediseño ----------
// El lienzo es un gris frío muy claro y las tarjetas van en blanco puro, como
// la referencia: la jerarquía la da la superficie, no el borde.
val CanvasLight = Color(0xFFF5F8F8)
val CardLight = Color(0xFFFFFFFF)
val TileFillLight = Color(0xFFF2F5F4)
val CardBorderLight = Color(0x0F050505)          // negro al 6%

val CanvasDark = Color(0xFF0A0C0B)
val CardDark = Color(0xFF141917)
val TileFillDark = Color(0xFF1C2421)
val CardBorderDark = Color(0x12FFFFFF)           // blanco al 7%

// ---------- Señales de resultado ----------
val PositiveLight = Color(0xFF0E7A54)
val PositiveDark = Color(0xFF5FD6A6)
val NegativeLight = Color(0xFFC0392B)
val NegativeDark = Color(0xFFFF8A7A)

// ---------- Acento de marca (verde TuBolsillo) ----------
val BrandGreen = Color(0xFF2A7C13)               // acento en claro (5.26:1)
val BrandGreenBright = Color(0xFF76C457)         // acento en oscuro (7.94:1)
val BrandGreenSoft = Color(0xFFD9EFC6)           // contenedor claro
val BrandGreenInk = Color(0xFF0F2A05)            // tinta sobre el contenedor
val BrandGreenDeep = Color(0xFF23421A)           // contenedor oscuro

// ---------- Señales ----------
val RedLight = Color(0xFFB03A2E)                 // error / deuda (6.02:1)
val RedDark = Color(0xFFFF9E90)                  // 8.54:1 sobre superficie
val RedFillLight = Color(0xFFF7DEDB)
val RedFillDark = Color(0xFF3A1A16)

val AmberLight = Color(0xFF8A6A1F)               // aviso (5.05:1)
val AmberDark = Color(0xFFE0C070)

/**
 * Paletas de acento seleccionables por el usuario en Ajustes -> Apariencia.
 * Cada variante define sus tonos calibrados para modo claro y oscuro.
 */
enum class AppAccent(
    val key: String,
    val label: String,
    val primaryLight: Color,
    val primaryDark: Color,
    val softContainerLight: Color,
    val deepContainerDark: Color,
    val ink: Color,
) {
    /**
     * Acento por defecto del rediseño. Dos tonos a propósito: [primaryLight]
     * (#0E7A54, 5.3:1 sobre blanco) es el que puede llevar texto; el verde
     * brillante de la referencia (#17A572, 3.2:1) solo se usa en rellenos y en
     * el degradado del saludo, nunca como tinta.
     */
    JADE(
        key = "JADE",
        label = "Jade",
        primaryLight = Color(0xFF0E7A54),
        primaryDark = Color(0xFF5FD6A6),
        softContainerLight = Color(0xFFDCF3E8),
        deepContainerDark = Color(0xFF0F3B2C),
        ink = Color(0xFF04241A),
    ),
    EMERALD(
        key = "EMERALD",
        label = "Esmeralda",
        primaryLight = BrandGreen,
        primaryDark = BrandGreenBright,
        softContainerLight = BrandGreenSoft,
        deepContainerDark = BrandGreenDeep,
        ink = BrandGreenInk,
    ),
    OCEAN(
        key = "OCEAN",
        label = "Océano",
        primaryLight = Color(0xFF0A66C2),
        primaryDark = Color(0xFF6BB2FF),
        softContainerLight = Color(0xFFD6E8FF),
        deepContainerDark = Color(0xFF12365E),
        ink = Color(0xFF06213E),
    ),
    TEAL(
        key = "TEAL",
        label = "Turquesa",
        primaryLight = Color(0xFF00796B),
        primaryDark = Color(0xFF5CD6C6),
        softContainerLight = Color(0xFFCCF2ED),
        deepContainerDark = Color(0xFF0F3D37),
        ink = Color(0xFF042622),
    ),
    INDIGO(
        key = "INDIGO",
        label = "Índigo",
        primaryLight = Color(0xFF4338CA),
        primaryDark = Color(0xFF9395FF),
        softContainerLight = Color(0xFFE0E0FF),
        deepContainerDark = Color(0xFF24215E),
        ink = Color(0xFF14113B),
    ),
    PLUM(
        key = "PLUM",
        label = "Ciruela",
        primaryLight = Color(0xFF7A288A),
        primaryDark = Color(0xFFD98CE6),
        softContainerLight = Color(0xFFF3D9F8),
        deepContainerDark = Color(0xFF42184B),
        ink = Color(0xFF2A0B30),
    ),
    CRIMSON(
        key = "CRIMSON",
        label = "Carmesí",
        primaryLight = Color(0xFFB8293D),
        primaryDark = Color(0xFFFF8A98),
        softContainerLight = Color(0xFFFCE0E3),
        deepContainerDark = Color(0xFF541620),
        ink = Color(0xFF380A11),
    ),
    AMBER(
        key = "AMBER",
        label = "Ámbar",
        primaryLight = Color(0xFF946200),
        primaryDark = Color(0xFFF5BE4F),
        softContainerLight = Color(0xFFFCE9C2),
        deepContainerDark = Color(0xFF473004),
        ink = Color(0xFF2B1C00),
    ),
    SLATE(
        key = "SLATE",
        label = "Grafito",
        primaryLight = Color(0xFF334155),
        primaryDark = Color(0xFF94A3B8),
        softContainerLight = Color(0xFFE2E8F0),
        deepContainerDark = Color(0xFF1E293B),
        ink = Color(0xFF0F172A),
    );

    companion object {
        fun fromKey(key: String?): AppAccent =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: JADE
    }
}

