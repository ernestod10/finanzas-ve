package dev.tubolsillo.app.data.parse.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CasheaParserTest {

    private val parser = CasheaParser()

    @Test
    fun `una compra en bolivares se registra en VES`() {
        val tx = parser.parse("Cashea: Tu compra en FARMACIA X fue aprobada por Bs. 100,00", 1_000L)
        assertEquals(-10_000L, tx?.amountMinor)
        assertEquals("VES", tx?.currency)
        assertEquals("FARMACIA X", tx?.merchant)
    }

    @Test
    fun `una compra en dolares se registra en USD`() {
        val tx = parser.parse("Cashea: Tu compra en TIENDA Y fue aprobada por \$25,50", 1_000L)
        assertEquals(-2_550L, tx?.amountMinor)
        assertEquals("USD", tx?.currency)
    }

    @Test
    fun `el recordatorio de cuota es un gasto`() {
        val tx = parser.parse("Cashea: recuerda pagar la cuota de Bs. 50,00 antes del 15", 1_000L)
        assertEquals(-5_000L, tx?.amountMinor)
        assertEquals("VES", tx?.currency)
    }

    @Test
    fun `un texto que no menciona Cashea no se parsea`() {
        assertNull(parser.parse("Compra por Bs. 10,00 en PANADERIA", 1_000L))
    }
}
