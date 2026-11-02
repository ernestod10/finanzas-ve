package dev.tubolsillo.app.ui.credits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.domain.model.CreditsOverview
import dev.tubolsillo.app.domain.repo.CreditsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.domain.repo.AccountRepository
import kotlinx.coroutines.launch

/** Deudas vivas (tarjetas, Cashea y cuotas) con su próxima fecha de pago. */
class CreditsViewModel(
    repository: CreditsRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {
    val overview: StateFlow<CreditsOverview> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreditsOverview())

    /**
     * Guarda el límite de una tarjeta. Es lo que falta para que la deuda salga
     * sola cuando el banco (BNC) solo publica el disponible: deuda = límite −
     * disponible.
     */
    fun setLimit(accountId: Long, currency: String, text: String) {
        val minor = AmountNormalizer.parseMinor(text, currency) ?: return
        if (minor <= 0) return
        viewModelScope.launch { accountRepository.updateCreditLimit(accountId, minor) }
    }
}
