package dev.tubolsillo.app.ui.credits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.domain.model.CreditsOverview
import dev.tubolsillo.app.domain.repo.CreditsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Deudas vivas (tarjetas, Cashea y cuotas) con su próxima fecha de pago. */
class CreditsViewModel(
    repository: CreditsRepository,
) : ViewModel() {
    val overview: StateFlow<CreditsOverview> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreditsOverview())
}
