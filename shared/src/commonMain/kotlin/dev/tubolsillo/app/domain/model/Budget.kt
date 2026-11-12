package dev.tubolsillo.app.domain.model

/** Semáforo del presupuesto: una sola regla para la pantalla y para Inicio. */
enum class BudgetLevel { OK, WARN, OVER }

/** Nombre del bucket que agrupa los gastos sin etiquetar. */
const val UNTAGGED_BUCKET = "Sin etiqueta"

/** Vista de una etiqueta en el presupuesto mensual. */
data class TagBudgetView(
    val tagName: String,
    val limitUsd: Double?,
    val spentUsd: Double,
    /** Color de la categoría, para la barra y la dona de gastos. */
    val colorHex: String? = null,
) {
    val remainingUsd: Double? get() = limitUsd?.minus(spentUsd)
    val progress: Float? get() = limitUsd?.let { if (it <= 0) null else (spentUsd / it).toFloat().coerceIn(0f, 1.5f) }
    val isOverBudget: Boolean get() = limitUsd != null && spentUsd > limitUsd

    val level: BudgetLevel
        get() = levelOf(spentUsd, limitUsd)
}

/** Estado completo del presupuesto de un mes. */
@androidx.compose.runtime.Immutable
data class BudgetMonthData(
    val month: String,
    val rows: List<TagBudgetView>,
    val totalSpentUsd: Double,
    val totalLimitUsd: Double?,
) {
    val totalRemainingUsd: Double? get() = totalLimitUsd?.minus(totalSpentUsd)
    val totalProgress: Float? get() = totalLimitUsd?.let { if (it <= 0) null else (totalSpentUsd / it).toFloat().coerceIn(0f, 1.5f) }

    val totalLevel: BudgetLevel get() = levelOf(totalSpentUsd, totalLimitUsd)
}

/** OVER si se pasó; WARN desde el 75 %; sin límite no hay semáforo. */
private fun levelOf(spentUsd: Double, limitUsd: Double?): BudgetLevel = when {
    limitUsd == null || limitUsd <= 0.0 -> BudgetLevel.OK
    spentUsd > limitUsd -> BudgetLevel.OVER
    spentUsd / limitUsd >= 0.75 -> BudgetLevel.WARN
    else -> BudgetLevel.OK
}
