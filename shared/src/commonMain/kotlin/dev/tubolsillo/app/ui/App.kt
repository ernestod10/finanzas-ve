package dev.tubolsillo.app.ui

import androidx.compose.runtime.Composable
import dev.tubolsillo.app.ui.navigation.TubolsilloNavHost
import dev.tubolsillo.app.ui.theme.TubolsilloTheme

/** Raíz de la UI compartida: tema + navegación. La invoca Android (MainActivity) e iOS. */
@Composable
fun App() {
    TubolsilloTheme {
        TubolsilloNavHost()
    }
}
