package dev.tubolsillo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.domain.model.RateSource
import dev.tubolsillo.app.domain.usecase.RateProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RatesViewModel @Inject constructor(
    private val rateProvider: RateProvider,
) : ViewModel() {

    /** Bolívares por dólar (lo que el usuario entiende). */
    val vesPerDollar: StateFlow<Double> = rateProvider.observeRates()
        .map { rates -> 1.0 / (rates["VES"] ?: 1.0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0)

    /** Guarda la tasa VES (texto = Bs. por USD); el modelo guarda USD por Bs. */
    fun saveVesRate(text: String) {
        val minor = AmountNormalizer.parseMinor(text, "VES") ?: return
        val vesPerDollar = minor / dev.tubolsillo.app.core.money.Money.pow10(2)
        if (vesPerDollar <= 0) return
        viewModelScope.launch {
            rateProvider.setRate("VES", 1.0 / vesPerDollar, RateSource.MANUAL)
        }
    }
}
