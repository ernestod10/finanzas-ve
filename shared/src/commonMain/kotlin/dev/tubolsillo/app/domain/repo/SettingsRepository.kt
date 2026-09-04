package dev.tubolsillo.app.domain.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.tubolsillo.app.core.platform.dataStorePath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Ajustes no sensibles de la app (DataStore KMP, archivo por plataforma vía
 * [dataStorePath]). Los secretos (llaves Binance) van en SecretStore.
 */
class SettingsRepository() {

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath {
        dataStorePath()
    }

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val DEFAULT_VES_RATE = stringPreferencesKey("default_ves_rate")
        val BINANCE_SYNC_MINUTES = intPreferencesKey("binance_sync_minutes")
        val DARK_MODE = stringPreferencesKey("dark_mode") // SYSTEM | LIGHT | DARK
    }

    val darkMode: Flow<String> =
        dataStore.data.map { it[Keys.DARK_MODE] ?: "SYSTEM" }

    suspend fun setDarkMode(mode: String) {
        dataStore.edit { it[Keys.DARK_MODE] = mode }
    }

    val onboardingDone: Flow<Boolean> =
        dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }
}
