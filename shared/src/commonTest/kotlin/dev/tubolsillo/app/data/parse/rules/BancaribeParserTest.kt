package dev.tubolsillo.app.data.parse.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BancaribeParserTest {

    private val parser = BancaribeParser()

    @Test
    fun `compra con cargo es negativa`() {
        val tx = parser.parse("Bancaribe: Compra por Bs. 1.234,56 en FARMACIA SAN JOSE. Saldo: Bs. 10.000,00", 1_000L)
        assertNotNull(tx)
        assertEquals(-123_456L, tx.amountMinor)
        assertEquals("VES", tx.currency)
        assertTrue(tx.merchant.contains("FARMACIA", ignoreCase = true))
    }

    @Test
    fun `pago movil recibido entra como positivo`() {
        val tx = parser.parse("Bancaribe: Pago movil recibido por Bs. 500,00 de 04141234567", 1_000L)
        assertNotNull(tx)
        assertEquals(50_000L, tx.amountMinor)
    }

    @Test
    fun `texto de otro banco no se parsea`() {
        assertNull(parser.parse("Banesco: consumo por Bs. 10,00", 1_000L))
    }
}
