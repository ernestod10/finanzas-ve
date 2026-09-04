package dev.tubolsillo.app.data.parse.rules

import dev.tubolsillo.app.domain.model.TransactionCategory
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Fixtures reales de push BDV (corpus sep-2026). */
class BdvParserTest {

    private val parser = BdvParser()

    @Test
    fun `pagomovil bdv recibido`() {
        val parsed = parser.parse(
            "PagomovilBDV recibido · Recibiste un PagomovilBDV por Bs.3.800,00 del 0424-2841075 Ref: 001600337354 en fecha 03-09-26 hora: 12:40.",
            occurredAt = 1_000L,
        )
        assertNotNull(parsed)
        assertEquals(380000L, parsed!!.amountMinor)
        assertEquals(TransactionCategory.PM_RECEIVED, parsed.category)
        assertEquals("0424-2841075", parsed.merchant)
    }

    @Test
    fun `transferencia bdv recibida`() {
        val parsed = parser.parse(
            "Transferencia BDV recibida · \"Recibiste una transferencia BDV de PABLO ALI FERNANDEZ SANCHEZ por Bs.13.000,00 bajo el numero de operacion 059130061855\"",
            occurredAt = 1_000L,
        )
        assertNotNull(parsed)
        assertEquals(1_300_000L, parsed!!.amountMinor)
        assertEquals(TransactionCategory.OTHER, parsed.category)
    }

    @Test
    fun `transferencia de otros bancos con saldo disponible`() {
        val parsed = parser.parse(
            "Transferencia de otros bancos recibida · Recibiste una transferencia de otros bancos de MENDIBLE SAYAGO ERNESTO DANIEL por Bs. 15.900,00 bajo el número de operación 68848899. Saldo disponible Bs. 15.900,63",
            occurredAt = 1_000L,
        )
        assertNotNull(parsed)
        assertEquals(1_590_000L, parsed!!.amountMinor)
        assertEquals(1_590_063L, parsed.balanceMinor)
    }

    @Test
    fun `pago sin contacto punto de venta con disp`() {
        val parsed = parser.parse(
            "Operaciones por Punto de venta / BiopagoBDV · Pago sin contacto realizado en punto de venta por Bs. 6.258,06, bajo el #0524, disp. 18.045,80 el 04-09-26 a las 08:32.",
            occurredAt = 1_000L,
        )
        assertNotNull(parsed)
        assertEquals(-625806L, parsed!!.amountMinor)
        assertEquals(TransactionCategory.MERCHANT, parsed.category)
        assertEquals(1_804_580L, parsed.balanceMinor) // "disp." = saldo disponible
    }

    @Test
    fun `operacion en linea con tarjeta debit`() {
        val parsed = parser.parse(
            "Pago en linea con tu Tarjeta Mastercard Debit Nacional · Operacion en linea con tu Tarjeta Mastercard Debit #2477 por Bs.4.193,06 el 03-09-26 a las 12:40.Inf:0500-6425283",
            occurredAt = 1_000L,
        )
        assertNotNull(parsed)
        assertEquals(-419306L, parsed!!.amountMinor)
        assertEquals(TransactionCategory.MERCHANT, parsed.category)
    }
}
