package dev.tubolsillo.app.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val darkMode by settings.darkMode.collectAsState(initial = "SYSTEM")
    val accentKey by settings.accentColor.collectAsState(initial = "JADE")
    val darkTheme = when (darkMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }
    val autoSync: dev.tubolsillo.app.data.source.portal.PortalAutoSync = koinInject()
    val ratesRefresher: dev.tubolsillo.app.domain.repo.RatesRefresher = koinInject()
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
        kotlinx.coroutines.delay(600)
        autoSync.runOnAppOpen()
    }
    TubolsilloTheme(
        darkTheme = darkTheme,
        accent = AppAccent.fromKey(accentKey),
    ) {
        // Primer arranque: onboarding antes de todo. Se lee en un efecto para
        // no mostrar el onboarding un instante a quien ya lo completó.
        var onboardingDone by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<Boolean?>(null)
        }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            onboardingDone = settings.isOnboardingDone()
        }
        when (onboardingDone) {
            null -> androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
            false -> dev.tubolsillo.app.ui.onboarding.OnboardingScreen(
                onDone = {
                    onboardingDone = true
                    scope.launch { settings.setOnboardingDone(true) }
                },
            )
            else -> {
                // El asistente flota sobre las pestañas, no sobre las pantallas
                // que se empujan (alta de registro, revisión, ajustes): ahí
                // estorba al teclado.
                TubolsilloNavHost()
            }
        }
    }
}
