package dev.tubolsillo.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.tubolsillo.app.domain.repo.SettingsRepository
import dev.tubolsillo.app.ui.navigation.TubolsilloNavHost
import dev.tubolsillo.app.ui.theme.AppAccent
import dev.tubolsillo.app.ui.theme.TubolsilloTheme
import org.koin.compose.koinInject

/** Raíz de la UI compartida: tema (con preferencia del usuario) + navegación. */
@Composable
fun App() {
    val settings: SettingsRepository = koinInject()
    val darkMode by settings.darkMode.collectAsState(initial = "SYSTEM")
    val accentKey by settings.accentColor.collectAsState(initial = "JADE")
    val darkTheme = when (darkMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }
    val autoSync: dev.tubolsillo.app.data.source.portal.PortalAutoSync = koinInject()
    val ratesRefresher: dev.tubolsillo.app.domain.repo.RatesRefresher = koinInject()
    val updateRepository: dev.tubolsillo.app.domain.repo.UpdateRepository = koinInject()
    val backgroundScheduler: dev.tubolsillo.app.core.platform.BackgroundScheduler = koinInject()
    val usdBackfill: dev.tubolsillo.app.domain.usecase.UsdBackfill = koinInject()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // El sync sigue siendo automático, pero espera a que la primera pantalla
        // esté pintada: abrir WebViews durante el arranque le robaba CPU al
        // primer frame y hacía que la app se sintiera pesada al abrir.
        kotlinx.coroutines.delay(900)
        runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                ratesRefresher.refreshIfStale()
            }
        }
        // Recordatorio diario de vencimientos (tarjetas y cuotas): se programa
        // una vez y WorkManager lo repite.
        // Rellena el USD congelado que falte (portal/notificaciones/CSV) y
        // corrige las monedas de 8 decimales. Idempotente.
        runCatching { usdBackfill.run() }
        runCatching { backgroundScheduler.scheduleDailyReminders() }
        kotlinx.coroutines.delay(800)
        // Consulta liviana a GitHub Releases al abrir: si hay versión nueva, la
        // fila de Menú queda en verde esperando el toque.
        runCatching { updateRepository.check() }
        kotlinx.coroutines.delay(600)
        autoSync.runOnAppOpen()
    }
    TubolsilloTheme(
        darkTheme = darkTheme,
        accent = AppAccent.fromKey(accentKey),
    ) {
        // El asistente flota sobre las pestañas, no sobre las pantallas que se
        // empujan (alta de registro, revisión, ajustes): ahí estorba al teclado.
        TubolsilloNavHost()
    }
}
