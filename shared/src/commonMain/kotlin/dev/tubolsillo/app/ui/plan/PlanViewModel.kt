package dev.tubolsillo.app.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.db.entity.CategoryEntity
import dev.tubolsillo.app.core.db.entity.PlanItemEntity
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.core.time.MonthBuckets
import dev.tubolsillo.app.domain.model.PlanKind
import dev.tubolsillo.app.domain.model.PlanMonthData
import dev.tubolsillo.app.domain.model.PlanRecurrence
import dev.tubolsillo.app.domain.model.PlanStatus
import dev.tubolsillo.app.domain.repo.CategoryRepository
import dev.tubolsillo.app.domain.repo.LinkResult
import dev.tubolsillo.app.domain.repo.PlanRepository
import dev.tubolsillo.app.domain.repo.TransactionRepository
import dev.tubolsillo.app.domain.usecase.SubscriptionDetector
import dev.tubolsillo.app.domain.usecase.SubscriptionSuggestion
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Plan del mes: líneas estimadas (ingresos y gastos) y su conciliación contra
 * lo real. El mes se cambia desde la barra, igual que en el presupuesto.
 */
class PlanViewModel(
    private val planRepository: PlanRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
) : ViewModel() {

    private val _month = MutableStateFlow(MonthBuckets.monthKeyNow())
    val month: StateFlow<String> = _month

    /** Sugerencias que el usuario descartó a mano; se olvidan al cambiar de mes. */
    private val _dismissed = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * Cobros que se repiten todos los meses (mismo comercio y monto). Se
     * calculan con aritmética sobre el historial, no con el modelo.
     */
    val subscriptions: StateFlow<List<SubscriptionSuggestion>> =
        transactionRepository.observeAll()
            .map { txs ->
                runCatching { SubscriptionDetector.detect(txs, dev.tubolsillo.app.core.time.nowMillis()) }
                    .getOrDefault(emptyList())
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)

    /** Aviso de una sola vez (p. ej. el movimiento ya era de otra línea). */
    val message: StateFlow<String?> = _message

    val monthData: StateFlow<PlanMonthData?> =
        combine(
            _month.flatMapLatest { planRepository.observeMonth(it) },
            _dismissed,
        ) { data, dismissed ->
            if (dismissed.isEmpty()) {
                data
            } else {
                data.copy(
                    income = data.income.hideSuggestions(dismissed),
                    expenses = data.expenses.hideSuggestions(dismissed),
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.categoriesState

    /** Línea que se está editando (se carga de la BD al abrir el diálogo). */
    private val _editing = MutableStateFlow<PlanItemEntity?>(null)
    val editing: StateFlow<PlanItemEntity?> = _editing

    fun nextMonth() {
        _month.value = MonthBuckets.plusMonths(_month.value, 1)
        _dismissed.value = emptySet()
    }

    fun previousMonth() {
        _month.value = MonthBuckets.plusMonths(_month.value, -1)
        _dismissed.value = emptySet()
    }

    /**
     * Crea una línea. El monto se guarda como magnitud positiva: la dirección
     * la lleva [PlanKind], no el signo (ver PlanCalculator).
     */
    /** Convierte una suscripción detectada en gasto fijo mensual del plan. */
    fun addSubscription(suggestion: SubscriptionSuggestion) {
        viewModelScope.launch {
            planRepository.addItem(
                kind = PlanKind.EXPENSE,
                label = suggestion.merchant,
                amountMinor = suggestion.amountMinor,
                currencyCode = suggestion.currency,
                recurrence = PlanRecurrence.MONTHLY,
                dueAt = null,
                dayOfMonth = suggestion.dayOfMonth,
                categoryId = null,
                month = _month.value,
            )
            _message.value = "«" + suggestion.merchant + "» agregado al plan"
        }
    }

    fun addItem(
        kind: PlanKind,
        label: String,
        amountText: String,
        currencyCode: String,
        recurrence: PlanRecurrence,
        dueAt: Long?,
        dayOfMonth: Int?,
        categoryId: Long?,
    ) {
        val minor = AmountNormalizer.parseMinor(amountText, currencyCode) ?: return
        viewModelScope.launch {
            planRepository.addItem(
                kind = kind,
                label = label.trim(),
                amountMinor = abs(minor),
                currencyCode = currencyCode,
                recurrence = recurrence,
                dueAt = dueAt,
                dayOfMonth = dayOfMonth,
                categoryId = categoryId,
                month = _month.value,
            )
            // Una línea puntual de otro mes: se salta a él para que se vea
            // recién creada en vez de parecer que no se guardó.
            if (recurrence == PlanRecurrence.ONCE && dueAt != null) {
                val target = MonthBuckets.monthKey(dueAt)
                if (target != _month.value) {
                    _month.value = target
                    _dismissed.value = emptySet()
                }
            }
        }
    }

    fun startEdit(itemId: Long) {
        viewModelScope.launch { _editing.value = planRepository.itemById(itemId) }
    }

    fun stopEdit() {
        _editing.value = null
    }

    /** Guarda los cambios de una línea existente (afecta a todos sus meses). */
    fun updateItem(
        item: PlanItemEntity,
        label: String,
        amountText: String,
        currencyCode: String,
        recurrence: PlanRecurrence,
        dueAt: Long?,
        dayOfMonth: Int?,
        categoryId: Long?,
    ) {
        val minor = AmountNormalizer.parseMinor(amountText, currencyCode) ?: return
        viewModelScope.launch {
            planRepository.updateItem(
                item.copy(
                    label = label.trim(),
                    amountMinor = abs(minor),
                    currency = currencyCode,
                    recurrence = recurrence.key,
                    dueAt = if (recurrence == PlanRecurrence.ONCE) dueAt else null,
                    dayOfMonth = if (recurrence == PlanRecurrence.MONTHLY) dayOfMonth else null,
                    startMonth = if (recurrence == PlanRecurrence.MONTHLY) item.startMonth else null,
                    categoryId = categoryId,
                )
            )
            _editing.value = null
        }
    }

    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            planRepository.deleteItem(itemId)
            _editing.value = null
        }
    }

    fun setStatus(itemId: Long, status: PlanStatus) {
        viewModelScope.launch { planRepository.setStatus(itemId, _month.value, status) }
    }

    /** Acepta la sugerencia: la línea queda pagada y enlazada a la transacción real. */
    fun linkTransaction(itemId: Long, txId: Long) {
        viewModelScope.launch {
            if (planRepository.linkTransaction(itemId, _month.value, txId) == LinkResult.AlreadyLinked) {
                _message.value = "Ese movimiento ya es el real de otra línea del plan."
            }
        }
    }

    fun dismissSuggestion(itemId: Long) {
        _dismissed.value = _dismissed.value + itemId
    }

    fun consumeMessage() {
        _message.value = null
    }
}

private fun List<dev.tubolsillo.app.domain.model.PlanLineView>.hideSuggestions(
    dismissed: Set<Long>,
) = map { if (it.itemId in dismissed) it.copy(suggestion = null) else it }
