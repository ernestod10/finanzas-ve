package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.entity.TransactionEntity
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/** Prueba la lógica pura del matcher de transferencias (funciones estáticas). */
class TransferMatcherTest {

    @Test
    fun `patas iguales dan ratio cero`() {
        assertEquals(0.0, TransferMatcher.ratio(100.0, 100.0)!!, 0.0001)
    }

    @Test
    fun `comision dentro de tolerancia`() {
        // Salida $100, entrada $99 (1% de comisión) → ratio 0.01.
        val r = TransferMatcher.ratio(100.0, 99.0)!!
        assertEquals(0.01, r, 0.0001)
        assertTrue(r <= TransferMatcher.TOLERANCE)
    }

    @Test
    fun `comision fuera de tolerancia no matchea`() {
        val r = TransferMatcher.ratio(100.0, 90.0)!!
        assertTrue(r > TransferMatcher.TOLERANCE)
    }

    @Test
    fun `monto cero no produce ratio`() {
        assertNull(TransferMatcher.ratio(0.0, 100.0))
        assertNull(TransferMatcher.ratio(100.0, 0.0))
    }

    @Test
    fun `confianza crece con cercania`() {
        assertTrue(TransferMatcher.baseConfidence(0.0005) > TransferMatcher.baseConfidence(0.015))
        assertEquals(0.95, TransferMatcher.baseConfidence(0.0001), 0.0001)
        assertEquals(0.70, TransferMatcher.baseConfidence(0.015), 0.0001)
    }

    @Test
    fun `keyword boost por texto de transferencia`() {
        val out = tx(1, "Transferencia enviada a V123")
        val inTx = tx(2, "Transferencia recibida de V456")
        assertEquals(0.05, TransferMatcher.keywordBoost(out, inTx), 0.0001)
    }

    @Test
    fun `sin keywords no hay boost`() {
        val a = tx(1, "FARMACIA X")
        val b = tx(2, "SUPERMERCADO Y")
        assertEquals(0.0, TransferMatcher.keywordBoost(a, b), 0.0001)
    }

    private fun tx(id: Long, description: String) = TransactionEntity(
        id = id,
        accountId = 1,
        occurredAt = 0,
        amountMinor = -100,
        currency = "USD",
        description = description,
        category = "OTHER",
        status = "CONFIRMED",
        source = "SMS",
    )
}
