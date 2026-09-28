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
