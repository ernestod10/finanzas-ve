package dev.tubolsillo.app.data.parse.rules

import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.TransactionCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MercantilPanamaParserTest {

    private val parser = MercantilPanamaParser()

    @Test
    fun `envio a zinli desde correo mercantil panama`() {
        val raw = """
Tu transferencia a Zinli
ha sido enviada
Tu
transferencia
a Zinli
ha sido
enviada
Ernesto daniel mendible sayago,
Tu envío de dinero a Zinli desde Mercantil BANCA MÓVIL se realizó con 
éxito. A continuación, encontrarás los detalles de tu transacción:
Ernesto daniel mendible sayago,
Tu envío de dinero a Zinli desde
Mercantil BANCA MÓVIL se realizó
con éxito. A continuación,
encontrarás los detalles de tu
transacción:
Detalles de tu transacción
Débito cuenta número: ******* 5624
Monto: $ 50.0
Concepto:
Fecha: 18/10/2026 12:13 pm
Beneficiario:
Correo del beneficiario: ernestodms10@gmail.com
        """.trimIndent()

        val parsed = parser.parse(raw, occurredAt = 1_000L)
        assertNotNull(parsed)
        assertEquals(Institution.MERCANTIL_PA, parsed.institution)
        assertEquals(-5000L, parsed.amountMinor)
        assertEquals("USD", parsed.currency)
        assertEquals("Zinli", parsed.merchant)
        assertEquals(TransactionCategory.TRANSFER, parsed.category)
    }

    @Test
    fun `notificacion gmail envio a zinli`() {
        val raw = "Mercantil Banco Panamá · Enviaste una transferencia a Zinli · Tu transferencia a Zinli ha sido enviada Ernesto daniel mendible sayago, Tu envío de dinero a Zinli desde Mercantil BANCA MÓVIL se realizó con éxito. A continuación, encontrarás los detalles de tu transacción: Detalles de tu transacción Débito cuenta número: ******* 5624 Monto: $ 50.0 Fecha: 18/10/2026 12:13 pm"

        val parsed = parser.parse(raw, occurredAt = 1_000L)
        assertNotNull(parsed)
        assertEquals(Institution.MERCANTIL_PA, parsed.institution)
        assertEquals(-5000L, parsed.amountMinor)
        assertEquals("USD", parsed.currency)
        assertEquals("Zinli", parsed.merchant)
        assertEquals(TransactionCategory.TRANSFER, parsed.category)
    }

    @Test
    fun `compra tarjeta debito desde correo mercantil panama`() {
        val raw = "Gracias por tu Compra por $ 7.47 en FARMACIA FARMAGO CA SA, con la tarjeta MasterCard Débito terminada en *3899."

        val parsed = parser.parse(raw, occurredAt = 1_000L)
        assertNotNull(parsed)
        assertEquals(Institution.MERCANTIL_PA, parsed.institution)
        assertEquals(-747L, parsed.amountMinor)
        assertEquals("USD", parsed.currency)
        assertEquals("FARMACIA FARMAGO CA SA", parsed.merchant)
        assertEquals(TransactionCategory.MERCHANT, parsed.category)
    }

    @Test
    fun `notificacion gmail compra tarjeta debito`() {
        val raw = "Mercantil Banco Panamá · Notificación de transacción · Gracias por tu Compra por $ 7.47 en FARMACIA FARMAGO CA SA, con la tarjeta MasterCard Débito terminada en *3899."

        val parsed = parser.parse(raw, occurredAt = 1_000L)
        assertNotNull(parsed)
        assertEquals(Institution.MERCANTIL_PA, parsed.institution)
        assertEquals(-747L, parsed.amountMinor)
        assertEquals("USD", parsed.currency)
        assertEquals("FARMACIA FARMAGO CA SA", parsed.merchant)
        assertEquals(TransactionCategory.MERCHANT, parsed.category)
    }

    @Test
    fun `compra con prefijo balboas B punto`() {
        val raw = "Compra por B/. 12.34 en FARMACIA X. Saldo: B/. 150.00"

        val parsed = parser.parse(raw, occurredAt = 1_000L)
        assertNotNull(parsed)
        assertEquals(Institution.MERCANTIL_PA, parsed.institution)
        assertEquals(-1234L, parsed.amountMinor)
        assertEquals("USD", parsed.currency)
        assertEquals("FARMACIA X", parsed.merchant)
        assertEquals(15000L, parsed.balanceMinor)
        assertEquals(TransactionCategory.MERCHANT, parsed.category)
    }

    @Test
    fun `ignora textos de venezuela con Bs`() {
        val raw = "Mercantil: Compra por Bs. 500,00 en TIENDA VE"
        val parsed = parser.parse(raw, occurredAt = 1_000L)
        assertNull(parsed)
    }
}
