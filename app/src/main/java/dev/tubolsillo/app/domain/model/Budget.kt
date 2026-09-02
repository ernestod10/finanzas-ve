package dev.tubolsillo.app.domain.model

/** Nombre del bucket que agrupa los gastos sin etiquetar. */
const val UNTAGGED_BUCKET = "Sin etiqueta"

/** Vista de una etiqueta en el presupuesto mensual. */
data class TagBudgetView(
    val tagName: String,
    val limitUsd: Double?,
    val spentUsd: Double,
) {
    val remainingUsd: Double? get() = limitUsd?.minus(spentUsd)
    val progress: Float? get() = limitUsd?.let { if (it <= 0) null else (spentUsd / it).toFloat().coerceIn(0f, 1.5f) }
    val isOverBudget: Boolean get() = limitUsd != null && spentUsd > limitUsd
}

/** Estado completo del presupuesto de un mes. */
data class BudgetMonthData(
    val month: String,
    val rows: List<TagBudgetView>,
    val totalSpentUsd: Double,
    val totalLimitUsd: Double?,
) {
    val totalRemainingUsd: Double? get() = totalLimitUsd?.minus(totalSpentUsd)
    val totalProgress: Float? get() = totalLimitUsd?.let { if (it <= 0) null else (totalSpentUsd / it).toFloat().coerceIn(0f, 1.5f) }
}
