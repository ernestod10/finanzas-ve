@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.tubolsillo.app.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.domain.model.BudgetMonthData
import dev.tubolsillo.app.domain.model.PlanMonthData
import dev.tubolsillo.app.domain.repo.BudgetRepository
import dev.tubolsillo.app.domain.repo.PlanRepository
import dev.tubolsillo.app.ui.period.PeriodState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BudgetViewModel(
    private val budgetRepository: BudgetRepository,
    private val planRepository: PlanRepository,
    private val periodState: PeriodState,
) : ViewModel() {

    /** Mismo mes que Inicio y Estadísticas. */
    val month: StateFlow<String> = periodState.month

    val monthData: StateFlow<BudgetMonthData?> =
        periodState.month.flatMapLatest { budgetRepository.observeMonth(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val planData: StateFlow<PlanMonthData?> =
        periodState.month.flatMapLatest { planRepository.observeMonth(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun nextMonth() {
        periodState.shift(1)
    }

    fun previousMonth() {
        periodState.shift(-1)
    }

    /** Guarda presupuesto; el texto del monto se parsea con AmountNormalizer (tolerante a VES). */
    fun setBudget(tagName: String, amountText: String, currencyCode: String) {
        val minor = AmountNormalizer.parseMinor(amountText, currencyCode) ?: return
        viewModelScope.launch {
            budgetRepository.setBudget(periodState.month.value, tagName, minor, currencyCode)
        }
    }

    fun togglePlanLinePaid(line: dev.tubolsillo.app.domain.model.PlanLineView) {
        val next = if (line.isPaid) dev.tubolsillo.app.domain.model.PlanStatus.PENDING
        else dev.tubolsillo.app.domain.model.PlanStatus.PAID
        viewModelScope.launch {
            planRepository.setStatus(line.itemId, periodState.month.value, next)
        }
    }
}
