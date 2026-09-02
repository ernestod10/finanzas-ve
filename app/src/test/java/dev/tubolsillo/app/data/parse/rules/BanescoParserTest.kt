package dev.tubolsillo.app.data.parse.rules

import dev.tubolsillo.app.domain.model.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BanescoParserTest {

    private val parser = BanescoParser()

    @Test
    fun `compra en punto con saldo`() {
        val parsed = parser.parse(
            "BanescoInforma: Compra por Bs. 25,00 en FARMACIA X, C.A. Saldo: Bs. 1.234,56",
            occurredAt = 1_000L,
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals(-2500L, parsed.amountMinor)
        // El punto final lo consume el regex del "Saldo:" que viene después.
        assertEquals("FARMACIA X, C.A", parsed.merchant)
        assertEquals(TransactionCategory.MERCHANT, parsed.category)
        assertEquals(123456L, parsed.balanceMinor)
        assertEquals("VES", parsed.currency)
    }

    @Test
    fun `compra TDC`() {
        val parsed = parser.parse(
            "BanescoInforma: Su tarjeta de credito terminacion 1234 ha sido utilizada por Bs. 1.200,00 en EXITO",
            occurredAt = 1_000L,
        )
        assertNotNull(parsed)
        assertEquals(-120000L, parsed!!.amountMinor)
    }

    @Test
    fun `pago movil recibido`() {
        val parsed = parser.parse(
            "BanescoInforma: Pago Movil recibido por Bs. 100,00 de V12345678. Saldo: Bs. 500,00",
            occurredAt = 1_000L,
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals(10000L, parsed.amountMinor)
        assertEquals(TransactionCategory.PM_RECEIVED, parsed.category)
        assertEquals("V12345678", parsed.merchant)
    }

    @Test
    fun `pago movil enviado es negativo`() {
        val parsed = parser.parse(
            "BanescoInforma: Pago Movil enviado por Bs. 30,00 a V87654321",
            occurredAt = 1_000L,
        )
        assertNotNull(parsed)
        assertEquals(-3000L, parsed!!.amountMinor)
        assertEquals(TransactionCategory.PM_SENT, parsed.category)
    }

    @Test
    fun `transferencia recibida es positiva`() {
        val parsed = parser.parse(
            "BanescoInforma: Transferencia recibida por Bs. 2.000,00",
            occurredAt = 1_000L,
        )
        assertNotNull(parsed)
        assertEquals(200000L, parsed!!.amountMinor)
    }

    @Test
    fun `texto de otra institucion no parsea`() {
        assertNull(parser.parse("Cashea: tu compra fue aprobada", occurredAt = 1_000L))
        assertNull(parser.parse("Mercantil Panamá: Compra por B/. 12.34", occurredAt = 1_000L))
    }

    @Test
    fun `monto con separador de miles VES`() {
        val parsed = parser.parse(
            "BanescoInforma: Compra por Bs. 1.234,56 en TIENDA X",
            occurredAt = 1_000L,
        )
        assertEquals(-123456L, parsed!!.amountMinor)
    }
}
