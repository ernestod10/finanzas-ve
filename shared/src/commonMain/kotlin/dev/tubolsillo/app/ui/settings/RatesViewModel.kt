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
) : ViewModel() {

    /** Bolívares por dólar (lo que el usuario entiende). */
    val vesPerDollar: StateFlow<Double> = rateProvider.observeRates()
        .map { rates -> 1.0 / (rates["VES"] ?: 1.0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0)

    /** Fuente seleccionada: BCV | P2P | MANUAL. */
    val rateSource: StateFlow<String> = settingsRepository.rateSource
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "BCV")

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _lastFetch = MutableStateFlow<RatesDto?>(null)
    val lastFetch: StateFlow<RatesDto?> = _lastFetch

    /** Cambia la fuente elegida (sin aplicar todavía). */
    fun selectSource(source: String) {
        viewModelScope.launch { settingsRepository.setRateSource(source) }
    }

    /** Guarda el cruce EUR→USD (usd/eur del BCV) para poder mostrar totales en €. */
    private fun saveEurRate(dto: RatesDto) {
        val usd = dto.bcv_usd_bs ?: return
        val eur = dto.bcv_eur_bs ?: return
        if (usd > 0 && eur > 0) {
            viewModelScope.launch {
                rateProvider.setRate("EUR", usd / eur, RateSource.BCV)
            }
        }
    }

    /** Ejecuta el fetch con la fuente seleccionada y aplica la tasa. */
    fun refresh() {
        val source = rateSource.value
        if (source == "MANUAL") return
        viewModelScope.launch {
            _busy.value = true
            _status.value = null
            try {
                val dto = ratesApi.fetch()
                _lastFetch.value = dto
                val bsPorUsd = when (source) {
                    "P2P" -> dto.p2p_usdt_ves
                    else -> dto.bcv_usd_bs
                }
                if (bsPorUsd != null && bsPorUsd > 0) {
                    rateProvider.setRate(
                        "VES", 1.0 / bsPorUsd,
                        if (source == "P2P") RateSource.PARALLEL else RateSource.BCV,
                    )
                    _status.value = "✓ Tasa actualizada (" +
                        (if (source == "P2P") "P2P Binance" else "BCV") + "): " +
                        dev.tubolsillo.app.core.money.MoneyFormatter.formatMinor(
                            (bsPorUsd * 100).toLong(), "VES",
                        ) + " por $ 1"
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
