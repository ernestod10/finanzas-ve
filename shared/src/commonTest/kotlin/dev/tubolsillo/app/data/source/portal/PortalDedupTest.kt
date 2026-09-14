package dev.tubolsillo.app.data.source.portal

import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.ParsedTransaction
import dev.tubolsillo.app.domain.model.TransactionCategory
import dev.tubolsillo.app.domain.usecase.DedupGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortalDedupTest {

    private val recipe = PortalRecipe(
        institution = Institution.BANESCO,
        dateFormat = "dd/MM/yyyy",
        currency = "VES",
    )

    private fun parsed(occurredAt: Long) = ParsedTransaction(
        institution = Institution.BANESCO,
        occurredAt = occurredAt,
        amountMinor = -12500,
        currency = "VES",
        merchant = "FARMATODO",
        description = "FARMATODO",
        category = TransactionCategory.MERCHANT,
        parserVersion = 1,
        raw = "compra farmatodo",
    )

    @Test
    fun `la clave exacta es la misma que usaria un SMS`() {
        val tx = parsed(1_700_000_000_000L)
        val plan = PortalDedup.plan(tx, recipe)
        val fromGate = DedupGate.companionDedupKey(
            tx.institution, tx.currency, tx.amountMinor, tx.occurredAt, tx.merchant,
        )
        assertEquals(fromGate, plan.exactKey)
    }

    @Test
    fun `las claves vecinas cubren la hora anterior y la siguiente`() {
        val tx = parsed(1_700_000_000_000L)
        val plan = PortalDedup.plan(tx, recipe)
        assertEquals(2, plan.neighborKeys.size)
        assertTrue(plan.neighborKeys.none { it == plan.exactKey })

        // La misma compra capturada por push una hora antes cae en la vecina.
        val push = parsed(1_700_000_000_000L - 60 * 60 * 1000)
        val pushKey = DedupGate.companionDedupKey(
            push.institution, push.currency, push.amountMinor, push.occurredAt, push.merchant,
        )
        assertTrue(plan.neighborKeys.contains(pushKey))
    }

    @Test
    fun `sin hora del dia hay ventana de dia`() {
        val plan = PortalDedup.plan(parsed(1_700_000_000_000L), recipe)
        assertNotNull(plan.dayWindow)
        assertEquals(24L * 60 * 60 * 1000, plan.dayWindow.toMs - plan.dayWindow.fromMs)
    }

    @Test
    fun `con hora del dia no hace falta la ventana`() {
        val conHora = recipe.copy(dateFormat = "dd/MM/yyyy HH:mm")
        assertNull(PortalDedup.plan(parsed(1_700_000_000_000L), conHora).dayWindow)
    }
}
