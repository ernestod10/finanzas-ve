package dev.tubolsillo.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.time.MonthBuckets
import dev.tubolsillo.app.domain.repo.AccountRepository
import dev.tubolsillo.app.domain.repo.DashboardData
import dev.tubolsillo.app.domain.repo.DashboardRepository
import dev.tubolsillo.app.domain.repo.PlanRepository
import dev.tubolsillo.app.domain.repo.TagRepository
import dev.tubolsillo.app.domain.repo.TransactionRepository
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.PlanMonthData
import dev.tubolsillo.app.domain.model.TransactionCategory
import dev.tubolsillo.app.domain.usecase.RateProvider
import dev.tubolsillo.app.domain.usecase.RuleEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    dashboardRepository: DashboardRepository,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository,
    private val ruleEngine: RuleEngine,
    private val rateProvider: RateProvider,
    private val categoryRepository: dev.tubolsillo.app.domain.repo.CategoryRepository,
    private val planRepository: PlanRepository,
    private val installmentDao: dev.tubolsillo.app.core.db.dao.InstallmentDao,
    statsRepository: dev.tubolsillo.app.domain.repo.StatsRepository,
    private val periodState: dev.tubolsillo.app.ui.period.PeriodState,
    settings: dev.tubolsillo.app.domain.repo.SettingsRepository,
    private val analyzer: dev.tubolsillo.app.domain.ai.CaptureAnalyzer,
) : ViewModel() {

    // --- Gemini Nano: alta en lenguaje natural y resumen del mes ---
    private val _analyzerAvailability =
        kotlinx.coroutines.flow.MutableStateFlow(dev.tubolsillo.app.domain.ai.AnalyzerAvailability.CHECKING)
    val analyzerAvailability: StateFlow<dev.tubolsillo.app.domain.ai.AnalyzerAvailability> =
        _analyzerAvailability

    private val _interpreted =
        kotlinx.coroutines.flow.MutableStateFlow<dev.tubolsillo.app.domain.ai.CaptureReading?>(null)
    val interpreted: StateFlow<dev.tubolsillo.app.domain.ai.CaptureReading?> = _interpreted

    private val _interpreting = kotlinx.coroutines.flow.MutableStateFlow(false)
    val interpreting: StateFlow<Boolean> = _interpreting

    /** Resúmenes ya generados, por mes ("2026-11"). */
    private val _summaries = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>(emptyMap())

    private val _summaryBusy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val summaryBusy: StateFlow<Boolean> = _summaryBusy

    init {
        viewModelScope.launch { _analyzerAvailability.value = analyzer.availability() }
    }

    /** Interpreta "gasté 20 en taxi ayer" para precargar el alta manual. */
    fun interpretEntry(text: String) {
        if (_interpreting.value || text.isBlank()) return
        viewModelScope.launch {
            _interpreting.value = true
            try {
                _interpreted.value = analyzer.interpretEntry(text)
            } finally {
                _interpreting.value = false
                _analyzerAvailability.value = analyzer.availability()
            }
        }
    }

    /**
     * Texto de una factura (OCR): lo interpreta el modelo con las categorías
     * reales y deja la lectura en el mismo flujo que el alta en lenguaje
     * natural, así el formulario se precarga igual.
     */
    fun analyzeReceipt(ocrText: String) {
        if (ocrText.isBlank()) return
        viewModelScope.launch {
            _interpreting.value = true
            try {
                _interpreted.value = analyzer.analyze(
                    rawText = ocrText.take(4000),
                    categories = categories.value.map { it.name },
                )
            } finally {
                _interpreting.value = false
                _analyzerAvailability.value = analyzer.availability()
            }
        }
    }

    fun clearInterpreted() {
        _interpreted.value = null
    }

    /** Redacta el resumen del mes con los números ya calculados. */
    fun generateSummary() {
        if (_summaryBusy.value) return
        viewModelScope.launch {
            _summaryBusy.value = true
            try {
                val data = stats.value ?: return@launch
                val plan = planSummary.value
                val top = data.expenseByCategory.take(3)
                    .joinToString(", ") { it.name + " " + dev.tubolsillo.app.core.money.MoneyFormatter.formatUsd(it.amountUsd) }
                val prompt = buildString {
                    append("Redactá un resumen financiero breve (máximo 3 frases) en español claro y directo, ")
                    append("sin consejos genéricos ni emojis. ")
                    append("Mes: ").append(dev.tubolsillo.app.ui.components.monthLabel(month.value)).append(". ")
                    append("Ingresos reales: ")
                    append(dev.tubolsillo.app.core.money.MoneyFormatter.formatUsd(data.metrics.incomeUsd)).append(". ")
                    append("Gastos reales: ")
                    append(dev.tubolsillo.app.core.money.MoneyFormatter.formatUsd(data.metrics.expenseUsd)).append(". ")
                    append("Flujo neto: ")
                    append(dev.tubolsillo.app.core.money.MoneyFormatter.formatUsd(data.metrics.netUsd)).append(". ")
                    if (top.isNotBlank()) append("Mayores gastos por categoría: ").append(top).append(". ")
                    if (plan != null && !plan.isEmpty) {
                        append("Plan del mes: faltan por pagar ")
                        append(dev.tubolsillo.app.core.money.MoneyFormatter.formatUsd(plan.pendingExpenseUsd)).append(". ")
                        plan.nextDue?.let { next ->
                            append("Próximo vencimiento: ").append(next.label).append(" el ")
                            append(dev.tubolsillo.app.ui.components.dayLabel(next.occursAt)).append(". ")
                        }
                        if (plan.overdueCount > 0) {
                            append("Hay ").append(plan.overdueCount).append(" línea(s) vencida(s). ")
                        }
                    }
                }
                val text = analyzer.summarize(prompt)
                if (text != null) {
                    _summaries.value = _summaries.value + (month.value to text)
                }
            } finally {
                _summaryBusy.value = false
                _analyzerAvailability.value = analyzer.availability()
            }
        }
    }

    /** Últimas referencias BCV/P2P persistidas, para "Tasas de hoy". */
    val ratesSnapshot: StateFlow<dev.tubolsillo.app.domain.repo.RatesSnapshot?> =
        settings.ratesSnapshot

    val dashboard: StateFlow<DashboardData?> = dashboardRepository.dashboardState

    /** Mes compartido con Estadísticas: cambiar el período aquí lo cambia allá. */
    val month: StateFlow<String> = periodState.month

    /** Resumen del mes seleccionado: cada mes tiene el suyo (o ninguno). */
    val summary: StateFlow<String?> = kotlinx.coroutines.flow.combine(_summaries, month) { map, m -> map[m] }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Plan del mes elegido. Sigue al período en vez de fijarse al crear el VM.
     */
    val planSummary: StateFlow<PlanMonthData?> =
        periodState.month
            .flatMapLatest { planRepository.observeMonth(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Métricas, serie de saldo y reparto de gasto del período elegido. */
    val stats: StateFlow<dev.tubolsillo.app.domain.repo.StatsData?> =
        periodState.month
            .flatMapLatest { statsRepository.observeMonth(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousMonth() {
        periodState.shift(-1)
    }

    fun nextMonth() {
        periodState.shift(1)
    }

    val categories: StateFlow<List<dev.tubolsillo.app.core.db.entity.CategoryEntity>> =
        categoryRepository.categoriesState

    val allTags: StateFlow<List<dev.tubolsillo.app.core.db.entity.TagEntity>> =
        tagRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tasas vigentes (USD por unidad), para las teselas de "Tasas de hoy". */
    val rates: StateFlow<Map<String, Double>> = rateProvider.ratesState

    /** Bolívares por dólar, para la sublínea VES del patrimonio neto. */
    val vesPerDollar: StateFlow<Double> = rateProvider.ratesState
        .map { rates -> 1.0 / (rates["VES"] ?: 1.0) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            1.0 / (rateProvider.ratesState.value["VES"] ?: 1.0),
        )

    fun addAccount(
        institution: Institution,
        name: String,
        kind: AccountKind,
        currencyCode: String,
        initialBalanceMinor: Long?,
        creditLimitMinor: Long?,
        colorHex: String? = null,
    ) {
        viewModelScope.launch {
            accountRepository.create(
                institution = institution,
                name = name,
                kind = kind,
                currencyCode = currencyCode,
                initialBalanceMinor = initialBalanceMinor,
                creditLimitMinor = creditLimitMinor,
                colorHex = colorHex,
            )
        }
    }

    /** Saca la cuenta de Inicio sin borrar sus movimientos (long-press en Inicio). */
    fun archiveAccount(accountId: Long) {
        viewModelScope.launch { accountRepository.archive(accountId) }
    }

    fun addManualTransaction(
        accountId: Long,
        amountMinor: Long,
        currencyCode: String,
        description: String,
        category: TransactionCategory,
        userCategoryId: Long? = null,
        casheaInstallmentMinor: Long? = null,
        occurredAt: Long = dev.tubolsillo.app.core.time.nowMillis(),
    ) {
        viewModelScope.launch {
            saveManualTransaction(
                accountId = accountId, amountMinor = amountMinor, currencyCode = currencyCode,
                description = description, category = category, userCategoryId = userCategoryId,
                casheaInstallmentMinor = casheaInstallmentMinor, occurredAt = occurredAt,
            )
        }
    }

    /** Igual que [addManualTransaction] pero suspendido: el formulario espera el resultado. */
    suspend fun saveManualTransaction(
        accountId: Long,
        amountMinor: Long,
        currencyCode: String,
        description: String,
        category: TransactionCategory,
        userCategoryId: Long? = null,
        casheaInstallmentMinor: Long? = null,
        occurredAt: Long = dev.tubolsillo.app.core.time.nowMillis(),
    ) {
            val now = occurredAt
            val txId = transactionRepository.insertManual(
                accountId = accountId,
                amountMinor = amountMinor,
                currencyCode = currencyCode,
                description = description,
                category = category,
                occurredAt = now,
            )
            // El alta manual mueve el saldo: sin esto, los totales de Inicio se
            // quedaban viejos hasta que el banco volviera a reportar saldo.
            accountRepository.applyTransactionDelta(
                accountId = accountId,
                deltaMinor = amountMinor,
                currencyCode = currencyCode,
            )
            if (userCategoryId != null) {
                transactionRepository.setUserCategory(txId, userCategoryId, learnMerchant = true)
            }
            // Compra de Cashea: el monto recibido es el TOTAL. Se reparte en 3
            // cuotas de total/3 cada 15 días y se guardan como cuotas pendientes
            // (antes se creaban 3 líneas del plan por el total: 3× el gasto).
            if (casheaInstallmentMinor != null && casheaInstallmentMinor > 0L) {
                val dayMs = 86_400_000L
                val perInstallment = casheaInstallmentMinor / 3
                val remainder = casheaInstallmentMinor - perInstallment * 3
                for (i in 1..3) {
                    installmentDao.insert(
                        dev.tubolsillo.app.core.db.entity.InstallmentEntity(
                            accountId = accountId,
                            dueAt = now + (i * 15L * dayMs),
                            amountMinor = perInstallment + if (i == 3) remainder else 0L,
                            currency = currencyCode,
                            status = "PENDING",
                        )
                    )
                }
            }
    }

    fun togglePlanLinePaid(line: dev.tubolsillo.app.domain.model.PlanLineView) {
        val next = if (line.isPaid) dev.tubolsillo.app.domain.model.PlanStatus.PENDING
        else dev.tubolsillo.app.domain.model.PlanStatus.PAID
        viewModelScope.launch {
            planRepository.setStatus(line.itemId, periodState.month.value, next)
        }
    }

    fun setTransactionCategory(transactionId: Long, categoryId: Long?, learnMerchant: Boolean = true) {
        viewModelScope.launch {
            transactionRepository.setUserCategory(transactionId, categoryId, learnMerchant)
        }
    }

    fun tagTransaction(transactionId: Long, tagName: String, learn: Boolean) {
        viewModelScope.launch {
            tagRepository.tagTransaction(transactionId, tagName)
            if (learn) {
                val tx = transactionRepository.getById(transactionId)
                tx?.let { ruleEngine.learn(it.description, tagName) }
            }
        }
    }

    fun updateTransactionDetails(transactionId: Long, description: String, amountMinor: Long, currencyCode: String) {
        viewModelScope.launch {
            transactionRepository.updateDetails(transactionId, description, amountMinor, currencyCode)
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            transactionRepository.delete(transactionId)
        }
    }
}
