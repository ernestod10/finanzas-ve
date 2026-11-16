package dev.tubolsillo.app.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.db.entity.SubscriptionEntity
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.domain.repo.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Suscripciones anotadas a mano. */
class SubscriptionsViewModel(
    private val repository: SubscriptionRepository,
) : ViewModel() {

    val items: StateFlow<List<SubscriptionEntity>> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun save(
        id: Long?,
        name: String,
        amountText: String,
        currency: String,
        dayText: String,
    ) {
        val amount = AmountNormalizer.parseMinor(amountText, currency)
        val day = dayText.toIntOrNull()?.takeIf { it in 1..31 }
        if (name.isBlank() || amount == null || amount <= 0 || day == null) {
            _message.value = "Escribe nombre, monto y un día entre 1 y 31."
            return
        }
        viewModelScope.launch {
            if (id == null) repository.add(name, amount, currency, day)
            else repository.update(id, name, amount, currency, day)
            _message.value = "Suscripción guardada."
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.archive(id) }
    }

    fun clearMessage() {
        _message.value = null
    }
}
