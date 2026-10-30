package dev.tubolsillo.app.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanMatcherTest {

    private val day = 24L * 60 * 60 * 1000
    private val due = 20 * day

    @Test
    fun `monto identico da ratio cero`() {
        assertEquals(0.0, PlanMatcher.amountRatio(60.0, 60.0)!!, 0.001)
    }

    @Test
    fun `diferencia de diez por ciento entra en tolerancia`() {
        // El ratio se mide contra el mayor: 6 sobre 66.
        val ratio = PlanMatcher.amountRatio(60.0, 66.0)!!
        assertEquals(0.0909, ratio, 0.001)
        assertTrue(ratio <= PlanMatcher.AMOUNT_TOLERANCE)
    }

    @Test
    fun `diferencia de veinte por ciento queda fuera`() {
        val ratio = PlanMatcher.amountRatio(60.0, 72.0)!!
        assertEquals(0.1667, ratio, 0.001)
        assertTrue(ratio > PlanMatcher.AMOUNT_TOLERANCE)
    }

    @Test
    fun `monto cero no produce ratio`() {
        assertNull(PlanMatcher.amountRatio(0.0, 60.0))
        assertNull(PlanMatcher.amountRatio(60.0, 0.0))
    }

    @Test
    fun `sugiere la candidata de monto y fecha casi exactos`() {
        val suggestion = PlanMatcher.suggest(
            estimatedUsd = 60.0,
            occursAt = due,
            candidates = listOf(PlanMatcher.Candidate(txId = 7, amountUsd = 60.0, occurredAt = due)),
        )
        assertNotNull(suggestion)
        assertEquals(7, suggestion.txId)
        assertEquals(0, suggestion.daysApart)
    }

    @Test
    fun `sin candidatas no hay sugerencia`() {
        assertNull(PlanMatcher.suggest(60.0, due, emptyList()))
    }

    @Test
    fun `fecha lejana descarta la sugerencia`() {
        val lejos = due + (PlanMatcher.WINDOW_DAYS + 1) * day
        assertNull(
            PlanMatcher.suggest(
                60.0,
                due,
                listOf(PlanMatcher.Candidate(1, 60.0, lejos)),
            )
        )
    }

    @Test
    fun `monto fuera de tolerancia descarta la sugerencia`() {
        assertNull(
            PlanMatcher.suggest(60.0, due, listOf(PlanMatcher.Candidate(1, 200.0, due)))
        )
    }

    @Test
    fun `confianza mayor cuando monto y fecha coinciden`() {
        val exacta = PlanMatcher.confidence(0.0, 0)
        val floja = PlanMatcher.confidence(PlanMatcher.AMOUNT_TOLERANCE, PlanMatcher.WINDOW_DAYS)
        kotlin.test.assertTrue(exacta > floja)
    }

    @Test
    fun `elegir la candidata de mayor confianza`() {
        val suggestion = PlanMatcher.suggest(
            60.0,
            due,
            listOf(
                PlanMatcher.Candidate(txId = 1, amountUsd = 68.0, occurredAt = due + 6 * day),
                PlanMatcher.Candidate(txId = 2, amountUsd = 60.0, occurredAt = due),
            ),
        )
        assertEquals(2, suggestion!!.txId)
    }

    @Test
    fun `a igual confianza elige la mas cercana en el tiempo`() {
        val suggestion = PlanMatcher.suggest(
            60.0,
            due,
            listOf(
                PlanMatcher.Candidate(txId = 1, amountUsd = 60.0, occurredAt = due - 3 * day),
                PlanMatcher.Candidate(txId = 2, amountUsd = 60.0, occurredAt = due + day),
            ),
        )
        assertEquals(2, suggestion!!.txId)
        assertEquals(1, suggestion.daysApart)
    }

    @Test
    fun `daysApart es en valor absoluto`() {
        assertEquals(2, PlanMatcher.daysApart(10 * day, 12 * day))
        assertEquals(2, PlanMatcher.daysApart(12 * day, 10 * day))
    }
}
