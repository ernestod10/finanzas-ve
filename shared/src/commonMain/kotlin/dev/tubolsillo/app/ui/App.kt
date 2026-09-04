package dev.tubolsillo.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.tubolsillo.app.domain.repo.SettingsRepository
import dev.tubolsillo.app.ui.navigation.TubolsilloNavHost
import dev.tubolsillo.app.ui.theme.TubolsilloTheme
import org.koin.compose.koinInject

/** Raíz de la UI compartida: tema (con preferencia del usuario) + navegación. */
@Composable
fun App() {
    val settings: SettingsRepository = koinInject()
    val darkMode by settings.darkMode.collectAsState(initial = "SYSTEM")
    val darkTheme = when (darkMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }
    TubolsilloTheme(darkTheme = darkTheme) {
        TubolsilloNavHost()
    }
}
