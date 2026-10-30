package dev.tubolsillo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.core.platform.BackgroundScheduler
import dev.tubolsillo.app.data.source.binance.BinanceSync
import dev.tubolsillo.app.data.source.binance.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BinanceSettingsViewModel(
    private val secretStore: SecretStore,
    private val binanceSync: BinanceSync,
    private val scheduler: BackgroundScheduler,
) : ViewModel() {

    private val _hasKeys = MutableStateFlow(false)
    val hasKeys: StateFlow<Boolean> = _hasKeys

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    init {
        viewModelScope.launch {
            _hasKeys.value = withContext(Dispatchers.Default) { secretStore.hasBinanceKeys() }
        }
    }

    fun saveKeys(apiKey: String, apiSecret: String) {
        if (apiKey.isBlank() || apiSecret.isBlank()) return
        viewModelScope.launch {
            _busy.value = true
            withContext(Dispatchers.Default) { secretStore.saveBinanceKeys(apiKey, apiSecret) }
            _hasKeys.value = true
            _status.value = "Llaves guardadas. Sincronizando…"
            scheduler.schedulePeriodicSync()
            val result = withContext(Dispatchers.Default) { binanceSync.sync() }
            _busy.value = false
            _status.value = if (result.isSuccess) {
                if (result.accountsUpdated == 0 && result.newTransactions == 0) "✓ Conectado: al día"
                else "✓ Conectado: ${result.accountsUpdated} cuentas, ${result.newTransactions} movimientos"
            } else {
                "✗ " + result.error
            }
        }
    }

    fun clearKeys() {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { secretStore.clearBinanceKeys() }
            _hasKeys.value = false
            _status.value = "Llaves eliminadas."
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _busy.value = true
            val result: SyncResult = withContext(Dispatchers.Default) { binanceSync.testConnection() }
            _busy.value = false
            _status.value = if (result.isSuccess) "✓ Conexión OK" else "✗ " + result.error
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _busy.value = true
            _status.value = "Sincronizando…"
            val result = withContext(Dispatchers.Default) { binanceSync.sync() }
            _busy.value = false
            _status.value = if (result.isSuccess) {
                if (result.accountsUpdated == 0 && result.newTransactions == 0) {
                    "✓ Sincronizado: al día (sin cambios)"
                } else {
                    "✓ Sincronizado: ${result.accountsUpdated} cuentas, ${result.newTransactions} movimientos"
                }
            } else {
                "✗ " + result.error
            }
        }
    }
}
