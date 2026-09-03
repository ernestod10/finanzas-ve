package dev.tubolsillo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.core.platform.BackgroundScheduler
import dev.tubolsillo.app.data.source.binance.BinanceSync
import dev.tubolsillo.app.data.source.binance.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BinanceSettingsViewModel(
    private val secretStore: SecretStore,
    private val binanceSync: BinanceSync,
    private val scheduler: BackgroundScheduler,
) : ViewModel() {

    val hasKeys: StateFlow<Boolean> = MutableStateFlow(secretStore.hasBinanceKeys())

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    fun saveKeys(apiKey: String, apiSecret: String) {
        if (apiKey.isBlank() || apiSecret.isBlank()) return
        secretStore.saveBinanceKeys(apiKey, apiSecret)
        (hasKeys as MutableStateFlow).value = true
        _status.value = "Llaves guardadas."
        scheduler.schedulePeriodicSync()
    }

    fun clearKeys() {
        secretStore.clearBinanceKeys()
        (hasKeys as MutableStateFlow).value = false
        _status.value = "Llaves eliminadas."
    }

    fun testConnection() {
        viewModelScope.launch {
            _busy.value = true
            val result: SyncResult = binanceSync.testConnection()
            _busy.value = false
            _status.value = if (result.isSuccess) "✓ Conexión OK" else "✗ " + result.error
        }
    }

    fun syncNow() {
        _status.value = "Sincronizando…"
        scheduler.syncNow()
    }
}
