package dev.tubolsillo.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.data.source.binance.BinanceSync
import dev.tubolsillo.app.data.source.binance.BinanceWorker
import dev.tubolsillo.app.data.source.binance.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BinanceSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secretStore: SecretStore,
    private val binanceSync: BinanceSync,
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
        BinanceWorker.schedulePeriodic(context)
    }

    fun clearKeys() {
        secretStore.clearBinanceKeys()
        (hasKeys as MutableStateFlow).value = false
        _status.value = "Llaves eliminadas."
    }

    fun testConnection() {
        viewModelScope.launch {
            _busy.value = true
            val result = binanceSync.testConnection()
            _busy.value = false
            _status.value = if (result.isSuccess) "✓ Conexión OK" else "✗ " + result.error
        }
    }

    fun syncNow() {
        _status.value = "Sincronizando…"
        BinanceWorker.syncNow(context)
    }
}
