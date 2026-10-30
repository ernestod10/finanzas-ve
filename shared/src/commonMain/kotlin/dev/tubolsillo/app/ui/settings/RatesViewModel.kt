package dev.tubolsillo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.data.source.rates.RatesApi
import dev.tubolsillo.app.data.source.rates.RatesDto
import dev.tubolsillo.app.domain.model.RateSource
import dev.tubolsillo.app.domain.repo.SettingsRepository
import dev.tubolsillo.app.domain.usecase.RateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Editor de tasas: la fuente (BCV/P2P/Manual) se elige en dropdown y se
 *  aplica con el botón "Actualizar tasa". */
class RatesViewModel(
    private val rateProvider: RateProvider,
    private val ratesApi: RatesApi,
    private val settingsRepository: SettingsRepository,
    private val ratesRefresher: dev.tubolsillo.app.domain.repo.RatesRefresher,
) : ViewModel() {

    /** Bolívares por dólar (lo que el usuario entiende). */
    val vesPerDollar: StateFlow<Double> = rateProvider.ratesState
        .map { rates -> 1.0 / (rates["VES"] ?: 1.0) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            1.0 / (rateProvider.ratesState.value["VES"] ?: 1.0),
        )

    /** Fuente seleccionada: BCV | P2P | MANUAL. */
    val rateSource: StateFlow<String> = settingsRepository.rateSource

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _lastFetch = MutableStateFlow<RatesDto?>(null)
    val lastFetch: StateFlow<RatesDto?> = _lastFetch

    init {
        viewModelScope.launch {
            if (rateSource.value != "MANUAL") {
                refresh()
            } else {
                // Aunque esté en manual, consultamos las referencias en segundo plano para la calculadora.
                runCatching { _lastFetch.value = ratesApi.fetch() }
            }
        }
    }

    /** Cambia la fuente elegida y si no es manual refresca de inmediato. */
    fun selectSource(source: String) {
        viewModelScope.launch {
            settingsRepository.setRateSource(source)
            if (source != "MANUAL") {
                refresh(overrideSource = source)
            }
        }
    }

    /** Ejecuta el fetch con la fuente seleccionada y aplica la tasa. */
    fun refresh(overrideSource: String? = null) {
        val source = overrideSource ?: rateSource.value
        if (source == "MANUAL") return
        viewModelScope.launch {
            _busy.value = true
            _status.value = null
            try {
                val dto = ratesRefresher.refresh(source)
                if (dto == null) {
                    _status.value = "✗ No se pudo conectar"
                    return@launch
                }
                _lastFetch.value = dto
                val bsPorUsd = when (source) {
                    "P2P" -> dto.p2p_usdt_ves
                    "EUR_BCV" -> dto.bcv_eur_bs
                    else -> dto.bcv_usd_bs
                }
                if (bsPorUsd != null && bsPorUsd > 0) {
                    val label = when (source) {
                        "P2P" -> "P2P Binance"
                        "EUR_BCV" -> "Euro BCV"
                        else -> "BCV"
                    }
                    _status.value = "Tasa actualizada ($label): " +
                        dev.tubolsillo.app.core.money.MoneyFormatter.formatMinor(
                            (bsPorUsd * 100).toLong(), "VES",
                        )
                } else {
                    _status.value = dto.error?.let { "✗ $it" }
                        ?: "✗ El endpoint no trajo la tasa solicitada"
                }
            } catch (e: Exception) {
                _status.value = "✗ No se pudo conectar: " + (e.message ?: "error")
            } finally {
                _busy.value = false
            }
        }
    }

    /** Guarda la tasa VES manual (texto = Bs. por USD) y cambia la fuente a Manual. */
    fun saveVesRate(text: String) {
        val minor = AmountNormalizer.parseMinor(text, "VES") ?: return
        val vesPerDollar = minor / dev.tubolsillo.app.core.money.Money.pow10(2)
        if (vesPerDollar <= 0) return
        viewModelScope.launch {
            rateProvider.setRate("VES", 1.0 / vesPerDollar, RateSource.MANUAL)
            settingsRepository.setRateSource("MANUAL")
            _status.value = "✓ Tasa manual guardada"
        }
    }
}
