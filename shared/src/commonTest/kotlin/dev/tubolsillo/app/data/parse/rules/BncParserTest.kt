package dev.tubolsillo.app.data.parse.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BncParserTest {

    private val parser = BncParser()

    @Test
    fun `compra con cargo es negativa y en bolivares`() {
        val tx = parser.parse("BNC: Compra por Bs. 250,00 en PANADERIA LA ESQUINA. Saldo: Bs. 1.000,00", 1_000L)
        assertNotNull(tx)
        assertEquals(-25_000L, tx.amountMinor)
        assertEquals("VES", tx.currency)
    }

    @Test
    fun `un aviso que no es movimiento no se parsea`() {
        assertNull(parser.parse("BNC: su clave sera bloqueada en 3 intentos", 1_000L))
    }
}
