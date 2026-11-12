package dev.tubolsillo.app.domain.model

import dev.tubolsillo.app.core.db.entity.SavingsContributionEntity
import dev.tubolsillo.app.core.db.entity.SavingsGoalEntity
import kotlin.math.abs

/** Un objetivo con su avance ya calculado. */
@androidx.compose.runtime.Immutable
data class SavingsGoalView(
    val id: Long,
    val name: String,
    val currency: String,
    val targetMinor: Long,
    val savedMinor: Long,
    val deadlineAt: Long?,
    val colorHex: String? = null,
) {
    val progress: Float
        get() = if (targetMinor <= 0) 0f else (savedMinor.toFloat() / targetMinor).coerceIn(0f, 1f)

    val remainingMinor: Long get() = (targetMinor - savedMinor).coerceAtLeast(0L)

    val isComplete: Boolean get() = savedMinor >= targetMinor

    /** Meses hasta la fecha límite (al menos 1 si aún no llegó). */
    fun monthsLeft(nowMs: Long): Int? {
        val deadline = deadlineAt ?: return null
        if (deadline <= nowMs) return 1
        val days = (deadline - nowMs) / 86_400_000L
        return ((days + 29) / 30).toInt().coerceAtLeast(1)
    }

    /** Cuánto habría que apartar por mes para llegar a tiempo. */
    fun perMonthNeeded(nowMs: Long): Long? {
        val months = monthsLeft(nowMs) ?: return null
        if (isComplete) return 0L
        return (remainingMinor + months - 1) / months
    }
}

/** Arma los objetivos a partir de las filas y sus aportes. */
object SavingsBuilder {
    fun build(
        goals: List<SavingsGoalEntity>,
        contributions: List<SavingsContributionEntity>,
    ): List<SavingsGoalView> {
        val byGoal = contributions.groupBy { it.goalId }
        return goals
            .filter { it.isActive }
            .map { goal ->
                val saved = byGoal[goal.id].orEmpty().sumOf { abs(it.amountMinor) }
                SavingsGoalView(
                    id = goal.id,
                    name = goal.name,
                    currency = goal.currency,
                    targetMinor = abs(goal.targetMinor),
                    savedMinor = saved,
                    deadlineAt = goal.deadlineAt,
                    colorHex = goal.colorHex,
                )
            }
    }
}
