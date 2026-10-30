package dev.tubolsillo.app.domain.model

/** Dirección de una línea del plan. */
enum class PlanKind(val key: String, val displayName: String) {
    INCOME("INCOME", "Ingreso"),
    EXPENSE("EXPENSE", "Gasto");

    companion object {
        fun fromKey(key: String?): PlanKind =
            entries.firstOrNull { it.key == key } ?: EXPENSE
    }
}

/** Cada cuánto se repite una línea del plan. */
enum class PlanRecurrence(val key: String, val displayName: String) {
    /** Una sola vez, en una fecha concreta (matrícula, reparación). */
    ONCE("ONCE", "Una vez"),

    /** Todos los meses en el mismo día (seguro, sueldo, alquiler). */
    MONTHLY("MONTHLY", "Cada mes");

    companion object {
        fun fromKey(key: String?): PlanRecurrence =
            entries.firstOrNull { it.key == key } ?: ONCE
    }
}

/** Estado de una línea dentro del mes. */
enum class PlanStatus(val key: String, val displayName: String) {
    PENDING("PENDING", "Pendiente"),
    PAID("PAID", "Pagado"),
    SKIPPED("SKIPPED", "Omitido");

    companion object {
        fun fromKey(key: String?): PlanStatus =
            entries.firstOrNull { it.key == key } ?: PENDING
    }
}

/**
 * Transacción real que probablemente corresponde a una línea del plan.
 * La produce [dev.tubolsillo.app.domain.usecase.PlanMatcher] y la UI la ofrece
 * para aceptar o descartar; nunca se enlaza sola.
 */
data class PlanSuggestion(
    val txId: Long,
    val amountUsd: Double,
    val occurredAt: Long,
    val daysApart: Long,
    val confidence: Double,
)

/**
 * Una ocurrencia del plan en el mes: la regla ya resuelta a una fecha y un
 * estado concretos. Es lo que la pantalla dibuja.
 */
data class PlanLineView(
    val itemId: Long,
    val kind: PlanKind,
    val label: String,
    val categoryId: Long?,
    val currencyCode: String,
    val amountMinor: Long,
    val amountUsd: Double,
    /** Fecha de vencimiento/cobro de esta ocurrencia (epoch ms). */
    val occursAt: Long,
    val isRecurring: Boolean,
    val status: PlanStatus,
    /** Transacción real enlazada, si ya se concilió. */
    val txId: Long?,
    /** Fila de estado existente, para poder actualizarla en vez de crearla. */
    val stateId: Long?,
    /** Pendiente y con fecha ya pasada. */
    val isOverdue: Boolean,
    /** Transacción real que parece corresponder a esta línea, si hay una. */
    val suggestion: PlanSuggestion? = null,
    /** Monto real de la transacción enlazada, en su moneda. Null si no hay enlace. */
    val settledMinor: Long? = null,
    val settledCurrencyCode: String? = null,
    /** El mismo monto real, en USD. */
    val settledUsd: Double? = null,
) {
    val isPaid: Boolean get() = status == PlanStatus.PAID
    val isPending: Boolean get() = status == PlanStatus.PENDING

    /** Real menos estimado; null si la línea no está conciliada. */
    val varianceUsd: Double? get() = settledUsd?.minus(amountUsd)

    /** Se pagó, pero por un monto distinto al estimado (el recibo subió). */
    val isSettledWithVariance: Boolean
        get() = varianceUsd?.let { kotlin.math.abs(it) > 0.5 } == true
}

/**
 * El plan de un mes enfrentado a lo real.
 *
 * Estimado = suma de las líneas del plan vigentes ese mes.
 * Real = transacciones del mes (sin transferencias internas), convertidas a USD.
 * Los dos lados se comparan por separado porque un mes puede cuadrar en el neto
 * y estar mal en cada mitad.
 */
@androidx.compose.runtime.Immutable
data class PlanMonthData(
    val month: String,
    val income: List<PlanLineView>,
    val expenses: List<PlanLineView>,
    val actualIncomeUsd: Double,
    val actualExpenseUsd: Double,
    /**
     * Cierto si alguna línea o transacción usa una moneda sin tasa configurada:
     * la conversión cae a 1.0 y el total es una aproximación que hay que decir
     * en voz alta, no esconder.
     */
    val ratesApproximate: Boolean = false,
) {
    val lines: List<PlanLineView> get() = income + expenses
    val estimatedIncomeUsd: Double get() = income.sumOf { it.amountUsd }
    val estimatedExpenseUsd: Double get() = expenses.sumOf { it.amountUsd }

    /** Lo que falta por pagar: líneas de gasto aún pendientes. */
    val pendingExpenseUsd: Double get() = expenses.filter { it.isPending }.sumOf { it.amountUsd }

    /** Lo que falta por cobrar. */
    val pendingIncomeUsd: Double get() = income.filter { it.isPending }.sumOf { it.amountUsd }

    val overdueCount: Int get() = (income + expenses).count { it.isOverdue }

    /** Real menos estimado: positivo = entró/gastó más de lo previsto. */
    val incomeDeltaUsd: Double get() = actualIncomeUsd - estimatedIncomeUsd
    val expenseDeltaUsd: Double get() = actualExpenseUsd - estimatedExpenseUsd

    fun estimatedBalanceUsd(): Double = estimatedIncomeUsd - estimatedExpenseUsd
    fun actualBalanceUsd(): Double = actualIncomeUsd - actualExpenseUsd

    val isEmpty: Boolean get() = income.isEmpty() && expenses.isEmpty()

    val settledCount: Int get() = lines.count { it.isPaid }
    val totalCount: Int get() = lines.size

    /** La próxima línea por vencer, para el resumen de Inicio. */
    val nextDue: PlanLineView? get() = lines.filter { it.isPending }.minByOrNull { it.occursAt }

    /** El gasto real ya superó lo estimado. */
    val expenseOver: Boolean get() = actualExpenseUsd > estimatedExpenseUsd
}
