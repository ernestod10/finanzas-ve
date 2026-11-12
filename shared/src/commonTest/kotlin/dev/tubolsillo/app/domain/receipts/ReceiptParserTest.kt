package dev.tubolsillo.app.domain.receipts

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReceiptParserTest {

    private val zone = TimeZone.of("America/Caracas")

    private val ticket = """
        FARMACIA SAN JOSE
        RIF J-30123456-7
        Av. Principal, Local 4
        Fecha: 12/11/2026
        ---------------------------------
        2 x PARACETAMOL 500mg     Bs. 240,00
        1 x ALCOHOL 250ml         Bs. 100,00
        SUBTOTAL                  Bs. 340,00
        IVA 16%                   Bs.  54,40
        TOTAL A PAGAR             Bs. 394,40
        GRACIAS POR SU COMPRA
    """.trimIndent()

    @Test
    fun `saca el total de la linea que dice TOTAL`() {
        val parsed = ReceiptParser.parse(ticket, zone = zone)
        assertEquals(39440L, parsed.totalMinor)
        assertTrue(parsed.totalExplained)
    }

    @Test
    fun `saca el comercio de la primera linea con letras`() {
        val parsed = ReceiptParser.parse(ticket, zone = zone)
        assertEquals("FARMACIA SAN JOSE", parsed.merchant)
    }

    @Test
    fun `saca la fecha del ticket`() {
        val parsed = ReceiptParser.parse(ticket, zone = zone)
        assertNotNull(parsed.occurredAt)
    }

    @Test
    fun `sin linea de total usa el monto mas grande`() {
        val parsed = ReceiptParser.parse(
            """
            ABARROTES LA ESQUINA
            12/11/2026
            ARROZ 1kg      Bs. 120,00
            ACEITE 1L      Bs. 450,00
            PAN            Bs.  80,00
            """.trimIndent(),
            zone = zone,
        )
        assertEquals(45000L, parsed.totalMinor)
        assertEquals(false, parsed.totalExplained)
        assertEquals("ABARROTES LA ESQUINA", parsed.merchant)
    }

    @Test
    fun `texto basura no inventa nada`() {
        val parsed = ReceiptParser.parse("", zone = zone)
        assertEquals(null, parsed.totalMinor)
        assertEquals(null, parsed.merchant)
    }
}
