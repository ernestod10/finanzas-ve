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

    @Test
    fun `el ocr mete espacios dentro del monto y se lee igual`() {
        // Las fotos reales producen "3.891, 46" y hasta "8. 746, 52".
        val parsed = ReceiptParser.parse(
            """
            FARMACIA SAN JOSE
            LAGRIMAS Bs 3.891, 46
            TOTAL Bs 8. 746, 52
            """.trimIndent(),
            zone = zone,
        )
        assertEquals(874652L, parsed.totalMinor)
        assertTrue(parsed.totalExplained)
    }

    @Test
    fun `el monto del total puede estar en la linea siguiente`() {
        val parsed = ReceiptParser.parse(
            """
            AUTOMERCADO EL SOL
            Fecha: 12/11/2026
            HARINA 1kg        Bs. 90,00
            TOTAL A PAGAR
            Bs. 1.234,56
            """.trimIndent(),
            zone = zone,
        )
        assertEquals(123456L, parsed.totalMinor)
        assertTrue(parsed.totalExplained)
    }

    @Test
    fun `importe tambien es total explicado`() {
        val parsed = ReceiptParser.parse(
            """
            FERRETERIA CENTRAL
            IMPORTE Bs 500,00
            """.trimIndent(),
            zone = zone,
        )
        assertEquals(50000L, parsed.totalMinor)
        assertTrue(parsed.totalExplained)
    }

    @Test
    fun `subtotal no gana sobre total`() {
        val parsed = ReceiptParser.parse(
            """
            PANADERIA LA ESPIGA
            SUBTOTAL     Bs. 900,00
            IVA          Bs. 144,00
            TOTAL        Bs. 1.044,00
            """.trimIndent(),
            zone = zone,
        )
        assertEquals(104400L, parsed.totalMinor)
        assertTrue(parsed.totalExplained)
    }

    @Test
    fun `fecha escrita con el mes en palabras`() {
        val parsed = ReceiptParser.parse(
            """
            BAR RESTAURANTE
            17 de mayo de 2026
            TOTAL Bs 250,00
            """.trimIndent(),
            zone = zone,
        )
        assertNotNull(parsed.occurredAt)
    }

    @Test
    fun `si el total no se lee lo saca de subtotal mas iva`() {
        // El OCR rompió la línea del total ("TOTAL ...."), pero la cuenta
        // cierra: 340,00 + 54,40 = 394,40.
        val parsed = ReceiptParser.parse(
            """
            FARMACIA SAN JOSE
            SUBTOTAL             Bs. 340,00
            IVA 16%              Bs.  54,40
            TOTAL ....
            GRACIAS POR SU COMPRA
            """.trimIndent(),
            zone = zone,
        )
        assertEquals(39440L, parsed.totalMinor)
        assertTrue(parsed.reconciled)
        assertEquals(39440L, parsed.candidatesMinor.first())
    }

    @Test
    fun `el descuento se resta en la cuenta`() {
        val parsed = ReceiptParser.parse(
            """
            TIENDA
            SUBTOTAL        Bs. 500,00
            DESCUENTO       Bs.  50,00
            TOTAL           Bs. 450,00
            """.trimIndent(),
            zone = zone,
        )
        assertEquals(45000L, parsed.totalMinor)
        assertTrue(parsed.reconciled)
    }

    @Test
    fun `los candidatos van ordenados y sin repetir`() {
        val parsed = ReceiptParser.parse(
            """
            ABARROTES
            SUBTOTAL        Bs. 340,00
            IVA 16%         Bs.  54,40
            TOTAL           Bs. 394,40
            """.trimIndent(),
            zone = zone,
        )
        assertEquals(39440L, parsed.candidatesMinor.first())
        assertEquals(parsed.candidatesMinor.distinct(), parsed.candidatesMinor)
        assertTrue(parsed.candidatesMinor.size <= 3)
    }

    @Test
    fun `el porcentaje del iva no se confunde con el monto`() {
        val parsed = ReceiptParser.parse(
            """
            TIENDA
            SUBTOTAL        Bs. 100,00
            IVA 16,00 %     Bs.  16,00
            TOTAL           Bs. 116,00
            """.trimIndent(),
            zone = zone,
        )
        assertEquals(11600L, parsed.totalMinor)
        assertTrue(parsed.reconciled)
    }
}
