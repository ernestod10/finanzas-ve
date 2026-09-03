package dev.tubolsillo.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.core.time.MonthBuckets
import dev.tubolsillo.app.domain.model.BudgetMonthData
import dev.tubolsillo.app.domain.repo.BudgetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BudgetViewModel(
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    private val _month = MutableStateFlow(MonthBuckets.monthKeyNow())
    val month: StateFlow<String> = _month

    val monthData: StateFlow<BudgetMonthData?> =
        _month.flatMapLatest { budgetRepository.observeMonth(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun nextMonth() {
        _month.value = java.time.YearMonth.parse(_month.value).plusMonths(1).toString()
    }

    fun previousMonth() {
        _month.value = java.time.YearMonth.parse(_month.value).minusMonths(1).toString()
    }

    /** Guarda presupuesto; el texto del monto se parsea con AmountNormalizer (tolerante a VES). */
    fun setBudget(tagName: String, amountText: String, currencyCode: String) {
        val minor = AmountNormalizer.parseMinor(amountText, currencyCode) ?: return
        viewModelScope.launch {
            budgetRepository.setBudget(_month.value, tagName, minor, currencyCode)
        }
    }
}
