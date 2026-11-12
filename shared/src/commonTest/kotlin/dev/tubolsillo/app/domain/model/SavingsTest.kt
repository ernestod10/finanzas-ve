package dev.tubolsillo.app.domain.model

import dev.tubolsillo.app.core.db.entity.SavingsContributionEntity
import dev.tubolsillo.app.core.db.entity.SavingsGoalEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SavingsTest {

    private val dayMs = 86_400_000L

    private fun goal(id: Long, target: Long, deadline: Long? = null, active: Boolean = true) =
        SavingsGoalEntity(
            id = id,
            name = "Meta $id",
            targetMinor = target,
            currency = "USD",
            deadlineAt = deadline,
            isActive = active,
            createdAt = 0,
        )

    private fun contribution(goalId: Long, amount: Long) = SavingsContributionEntity(
        goalId = goalId,
        amountMinor = amount,
        contributedAt = 0,
    )

    @Test
    fun `el avance es la suma de los aportes`() {
        val views = SavingsBuilder.build(
            listOf(goal(1, 100_000)),
            listOf(contribution(1, 30_000), contribution(1, 20_000)),
        )
        val view = views.single()
        assertEquals(50_000, view.savedMinor)
        assertEquals(50_000, view.remainingMinor)
        assertEquals(0.5f, view.progress)
    }

    @Test
    fun `cuanto apartar por mes segun la fecha limite`() {
        val now = 1_000_000_000_000L
        // Faltan 120.000 en 4 meses (120 días)
        val view = SavingsBuilder.build(
            listOf(goal(1, 200_000, deadline = now + 120 * dayMs)),
            listOf(contribution(1, 80_000)),
        ).single()
        assertEquals(30_000L, view.perMonthNeeded(now))
    }

    @Test
    fun `sin fecha no hay cuota mensual y una meta cumplida no pide nada`() {
        val now = 1_000_000_000_000L
        val sinFecha = SavingsBuilder.build(listOf(goal(1, 100_000)), emptyList()).single()
        assertEquals(null, sinFecha.perMonthNeeded(now))

        val cumplida = SavingsBuilder.build(
            listOf(goal(2, 100_000, deadline = now + 30 * dayMs)),
            listOf(contribution(2, 120_000)),
        ).single()
        assertTrue(cumplida.isComplete)
        assertEquals(0L, cumplida.perMonthNeeded(now))
        assertEquals(0L, cumplida.remainingMinor)
    }

    @Test
    fun `los objetivos archivados no se muestran`() {
        val views = SavingsBuilder.build(
            listOf(goal(1, 100_000, active = false), goal(2, 100_000)),
            emptyList(),
        )
        assertEquals(1, views.size)
        assertEquals(2L, views.single().id)
    }
}
