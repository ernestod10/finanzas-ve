package dev.tubolsillo.app.data.source.portal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PortalTdcParserTest {

    /** Texto real de Conexión Digital (Bancaribe), oct-2026. */
    private val bancaribe = """
        Tarjetas de Crédito Visa Avant Estand 4812***1901 Paga antes del 15-11-2026
        Disp. Bs. 2.467,45 Deuda Bs. 22.232,55 Pago min. Bs. 3.705,43
    """

    @Test
    fun `lee disponible, deuda, minimo y fecha de la tarjeta Bancaribe`() {
        val tdc = PortalTdcParser.parse(bancaribe)

        assertEquals(246745L, tdc.availableMinor)
        assertEquals(2223255L, tdc.debtMinor)
        assertEquals(370543L, tdc.minPaymentMinor)
        assertEquals("4812***1901", tdc.mask)
        assertEquals("Visa Avant Estand", tdc.cardName)
        assertEquals("VES", tdc.currency)
        assertNotNull(tdc.dueAt)
    }

    @Test
    fun `el limite se deduce de deuda mas disponible`() {
        val tdc = PortalTdcParser.parse(bancaribe)
        assertEquals(2470000L, tdc.effectiveLimitMinor)
    }

    @Test
    fun `el 15 de noviembre de 2026 cae en el dia esperado`() {
        val tdc = PortalTdcParser.parse(bancaribe)
        // 2026-11-15T12:00Z: mediodía UTC para que no se corra el día
        assertEquals(1_794_744_000_000L, tdc.dueAt)
    }

    @Test
    fun `lee el formato de BNC con saldo actual y disponible`() {
        val tdc = PortalTdcParser.parse(
            "TARJETA DE CRÉDITO VISA CLASSIC 4907******1234 " +
                "Saldo actual Bs. 8.400,00 Disponible Bs. 1.600,00 Pago mínimo Bs. 1.260,00 Vence 05/12/2026"
        )
        assertEquals("4907******1234", tdc.mask)
        assertEquals(840000L, tdc.debtMinor)
        assertEquals(160000L, tdc.availableMinor)
        assertEquals(126000L, tdc.minPaymentMinor)
        assertEquals(1_000_000L, tdc.effectiveLimitMinor)
    }

    @Test
    fun `la fecha iso tambien se entiende`() {
        val tdc = PortalTdcParser.parse("Visa 4512********7890 Vence 2026-12-05 Deuda Bs. 100,00")
        assertEquals(1_796_472_000_000L, tdc.dueAt)
    }

    @Test
    fun `un monto en dolares se marca como USD`() {
        val tdc = PortalTdcParser.parse("Visa Platinum 4512****7890 Deuda USD 120,50 Disp. USD 379,50")
        assertEquals("USD", tdc.currency)
        assertEquals(12050L, tdc.debtMinor)
        assertEquals(37950L, tdc.availableMinor)
        assertEquals(50000L, tdc.effectiveLimitMinor)
    }

    @Test
    fun `BNC imprime la moneda entre parentesis y solo el disponible`() {
        val tdc = PortalTdcParser.parse(
            "Tarjetas de Crédito MasterCard Gold-SOY TECHNO BNC ***0008 " +
                "Ernesto D. Mendible Disponible (Bs.): 114.311,14"
        )
        assertEquals(11431114L, tdc.availableMinor)
        assertNull(tdc.debtMinor)
        assertEquals("***0008", tdc.mask)
        assertEquals("MasterCard Gold-SOY TECHNO BNC", tdc.cardName)
        assertEquals("VES", tdc.currency)
    }

    @Test
    fun `sin etiquetas de tarjeta no inventa montos`() {
        val tdc = PortalTdcParser.parse("Tarjetas de Crédito")
        assertNull(tdc.debtMinor)
        assertNull(tdc.availableMinor)
        assertNull(tdc.dueAt)
        assertEquals(false, tdc.hasAmounts)
    }
}
