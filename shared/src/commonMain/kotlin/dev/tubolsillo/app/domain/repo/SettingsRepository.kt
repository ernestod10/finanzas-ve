package dev.tubolsillo.app.domain.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.tubolsillo.app.core.platform.dataStorePath
import dev.tubolsillo.app.core.time.nowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Último snapshot de tasas publicado por el endpoint (todas en bolívares por
 * unidad). Se guarda para que Inicio pueda mostrar las referencias reales.
 */
data class RatesSnapshot(
    val bcvUsdBs: Double,
    val bcvEurBs: Double,
    val p2pUsdtVes: Double,
    val fetchedAt: Long,
)

/**
 * Ajustes no sensibles de la app (DataStore KMP, archivo por plataforma vía
 * [dataStorePath]). Los secretos (llaves Binance) van en SecretStore.
 */
class SettingsRepository() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath {
        dataStorePath()
    }

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val DEFAULT_VES_RATE = stringPreferencesKey("default_ves_rate")
        val BINANCE_SYNC_MINUTES = intPreferencesKey("binance_sync_minutes")
        val DARK_MODE = stringPreferencesKey("dark_mode") // SYSTEM | LIGHT | DARK
        val ACCENT_COLOR = stringPreferencesKey("accent_color") // JADE | EMERALD | OCEAN | TEAL | INDIGO | PLUM | CRIMSON | AMBER | SLATE
        val RATE_SOURCE = stringPreferencesKey("rate_source") // BCV | P2P | MANUAL
        val AUTO_SYNC_PORTALS = booleanPreferencesKey("auto_sync_portals_on_open")
        // Último snapshot de tasas del endpoint, para que Inicio muestre las
        // referencias reales (BCV dólar/euro y P2P USDT) sin depender de que el
        // usuario tenga la pantalla de tasas abierta.
        val BCV_USD_BS = doublePreferencesKey("rates_bcv_usd_bs")
        val BCV_EUR_BS = doublePreferencesKey("rates_bcv_eur_bs")
        val P2P_USDT_VES = doublePreferencesKey("rates_p2p_usdt_ves")
        val RATES_FETCHED_AT = longPreferencesKey("rates_fetched_at")
    }

    val rateSource: StateFlow<String> =
        dataStore.data.map { it[Keys.RATE_SOURCE] ?: "BCV" }
            .stateIn(scope, SharingStarted.Eagerly, "BCV")

    suspend fun setRateSource(source: String) {
        dataStore.edit { it[Keys.RATE_SOURCE] = source }
    }

    /** Referencias del endpoint; null mientras no haya habido un fetch válido. */
    val ratesSnapshot: StateFlow<RatesSnapshot?> =
        dataStore.data.map { prefs ->
            val usd = prefs[Keys.BCV_USD_BS]
            if (usd == null || usd <= 0.0) null
            else RatesSnapshot(
                bcvUsdBs = usd,
                bcvEurBs = prefs[Keys.BCV_EUR_BS] ?: 0.0,
                p2pUsdtVes = prefs[Keys.P2P_USDT_VES] ?: 0.0,
                fetchedAt = prefs[Keys.RATES_FETCHED_AT] ?: 0L,
            )
        }.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun setRatesSnapshot(bcvUsdBs: Double?, bcvEurBs: Double?, p2pUsdtVes: Double?) {
        if (bcvUsdBs == null || bcvUsdBs <= 0.0) return
        dataStore.edit { prefs ->
            prefs[Keys.BCV_USD_BS] = bcvUsdBs
            if (bcvEurBs != null && bcvEurBs > 0.0) prefs[Keys.BCV_EUR_BS] = bcvEurBs
            if (p2pUsdtVes != null && p2pUsdtVes > 0.0) prefs[Keys.P2P_USDT_VES] = p2pUsdtVes
            prefs[Keys.RATES_FETCHED_AT] = nowMillis()
        }
    }

    val darkMode: StateFlow<String> =
        dataStore.data.map { it[Keys.DARK_MODE] ?: "SYSTEM" }
            .stateIn(scope, SharingStarted.Eagerly, "SYSTEM")

    suspend fun setDarkMode(mode: String) {
        dataStore.edit { it[Keys.DARK_MODE] = mode }
    }

    val accentColor: StateFlow<String> =
        dataStore.data.map { it[Keys.ACCENT_COLOR] ?: "JADE" }
            .stateIn(scope, SharingStarted.Eagerly, "JADE")

    suspend fun setAccentColor(accentKey: String) {
        dataStore.edit { it[Keys.ACCENT_COLOR] = accentKey }
    }

    val onboardingDone: StateFlow<Boolean> =
        dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }
            .stateIn(scope, SharingStarted.Eagerly, false)

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    /** Lectura directa (no el valor inicial del StateFlow). */
    suspend fun isOnboardingDone(): Boolean =
        dataStore.data.first()[Keys.ONBOARDING_DONE] ?: false

    val autoSyncPortals: StateFlow<Boolean> =
        dataStore.data.map { it[Keys.AUTO_SYNC_PORTALS] ?: true }
            .stateIn(scope, SharingStarted.Eagerly, true)

    suspend fun autoSyncPortalsOnOpen(): Boolean =
        dataStore.data.first()[Keys.AUTO_SYNC_PORTALS] ?: true

    suspend fun setAutoSyncPortals(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_SYNC_PORTALS] = enabled }
    }
}
