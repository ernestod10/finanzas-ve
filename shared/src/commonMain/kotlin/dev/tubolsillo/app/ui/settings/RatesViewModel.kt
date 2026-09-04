package dev.tubolsillo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.data.source.rates.RatesApi
import dev.tubolsillo.app.data.source.rates.RatesDto
import dev.tubolsillo.app.domain.model.RateSource
import dev.tubolsillo.app.domain.usecase.RateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Editor de tasas con fetch automático (BCV / P2P) desde el endpoint. */
class RatesViewModel(
    private val rateProvider: RateProvider,
    private val ratesApi: RatesApi,
) : ViewModel() {

    /** Bolívares por dólar (lo que el usuario entiende). */
    val vesPerDollar: StateFlow<Double> = rateProvider.observeRates()
        .map { rates -> 1.0 / (rates["VES"] ?: 1.0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0)

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _lastFetch = MutableStateFlow<RatesDto?>(null)
    val lastFetch: StateFlow<RatesDto?> = _lastFetch

    /** Guarda la tasa VES (texto = Bs. por USD); el modelo guarda USD por Bs. */
    fun saveVesRate(text: String) {
        val minor = AmountNormalizer.parseMinor(text, "VES") ?: return
        val vesPerDollar = minor / dev.tubolsillo.app.core.money.Money.pow10(2)
        if (vesPerDollar <= 0) return
        viewModelScope.launch {
            rateProvider.setRate("VES", 1.0 / vesPerDollar, RateSource.MANUAL)
        }
    }

    /** Trae las tasas del endpoint y aplica la elegida ([source] BCV o P2P). */
    fun fetchAndApply(source: RateSource) {
        viewModelScope.launch {
            _busy.value = true
            _status.value = null
            try {
                val dto = ratesApi.fetch()
                _lastFetch.value = dto
                val bsPorUsd = when (source) {
                    RateSource.BCV -> dto.bcv_usd_bs
                    RateSource.PARALLEL -> dto.p2p_usdt_ves
                    else -> null
                }
                if (bsPorUsd != null && bsPorUsd > 0) {
                    rateProvider.setRate("VES", 1.0 / bsPorUsd, source)
                    _status.value = "✓ Tasa aplicada: " +
                        dev.tubolsillo.app.core.money.MoneyFormatter.formatMinor(
                            (bsPorUsd * 100).toLong(), "VES",
                        ) + " por \$ 1"
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
}
