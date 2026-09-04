package dev.tubolsillo.app.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta de TuBolsillo — "papel + bosque": fondos cálidos de papel, tinta
// cálida, UN solo acento (verde bosque desaturado) y terracota solo para deuda.

// --- Luz ---
val PaperLight = Color(0xFFF6F4EF)        // fondo cálido, no blanco puro
val PaperSurfaceLight = Color(0xFFFFFFFF)
val InkLight = Color(0xFF221F1A)          // tinta cálida (no negro puro)
val MutedLight = Color(0xFF6F6A60)

val ForestLight = Color(0xFF2E5E43)       // acento único (verde profundo)
val OnForestLight = Color(0xFFFFFFFF)
val ForestContainerLight = Color(0xFFDCE8DF)
val OnForestContainerLight = Color(0xFF123326)
val SageLight = Color(0xFF6B705F)         // secundario oliva, solo iconos suaves
val GoldLight = Color(0xFFB08A3E)         // terciario: pago móvil / alertas
val ClayLight = Color(0xFFA63D36)         // error/deuda (terracota)

// --- Oscuro ---
val CharcoalDark = Color(0xFF131410)      // carbón cálido, no negro puro
val SurfaceDark = Color(0xFF1C1D16)
val InkDark = Color(0xFFECE9E2)
val MutedDark = Color(0xFFB9B4A8)

val ForestDark = Color(0xFF9CC5A8)
val OnForestDark = Color(0xFF0F241A)
val ForestContainerDark = Color(0xFF23432F)
val OnForestContainerDark = Color(0xFFDCE8DF)
val SageDark = Color(0xFFA8AC99)
val GoldDark = Color(0xFFD9B96B)
val ClayDark = Color(0xFFE3A29A)

// Colores semánticos de montos (independientes del tema claro/oscuro).
val PositiveLight = Color(0xFF2E5E43)
val NegativeLight = Color(0xFFA63D36)
val PositiveDark = Color(0xFF9CC5A8)
val NegativeDark = Color(0xFFE3A29A)
