package dev.tubolsillo.app.ui.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.domain.model.SavingsGoalView
import dev.tubolsillo.app.domain.repo.SavingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Objetivos de ahorro: metas, aportes y cuánto falta por mes. */
class SavingsViewModel(
    private val repository: SavingsRepository,
) : ViewModel() {

    val goals: StateFlow<List<SavingsGoalView>> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun addGoal(name: String, targetText: String, deadlineAt: Long?, currency: String = "USD") {
        val target = AmountNormalizer.parseMinor(targetText, currency) ?: return
        if (name.isBlank() || target <= 0) {
            _message.value = "Escribe un nombre y un monto mayor que cero."
            return
        }
        viewModelScope.launch {
            repository.addGoal(name, target, currency, deadlineAt)
            _message.value = "Objetivo «$name» creado."
        }
    }

    fun contribute(goal: SavingsGoalView, amountText: String) {
        val amount = AmountNormalizer.parseMinor(amountText, goal.currency) ?: return
        if (amount <= 0) return
        viewModelScope.launch {
            repository.contribute(goal.id, amount)
            _message.value = "Aporte registrado en «${goal.name}»."
        }
    }

    fun archive(goal: SavingsGoalView) {
        viewModelScope.launch {
            repository.archive(goal.id)
            _message.value = "Objetivo «${goal.name}» eliminado."
        }
    }

    fun now(): Long = nowMillis()
}
