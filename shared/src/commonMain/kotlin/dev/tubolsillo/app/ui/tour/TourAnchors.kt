package dev.tubolsillo.app.ui.tour

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * Zonas que el tour puede iluminar. Las rellena quien las dibuja (la barra
 * inferior) para no adivinar coordenadas ni tamaños de pantalla.
 */
class TourAnchors {
    var fab: Rect? by mutableStateOf(null)
    var bar: Rect? by mutableStateOf(null)
}

val LocalTourAnchors = compositionLocalOf<TourAnchors?> { null }
