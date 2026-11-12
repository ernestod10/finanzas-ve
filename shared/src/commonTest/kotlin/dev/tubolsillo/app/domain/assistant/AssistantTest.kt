package dev.tubolsillo.app.domain.assistant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistantTest {

    private val categories = listOf("Comida", "Salud", "Transporte")

    // --- Intención ---

    @Test
    fun `lee una peticion de estadisticas`() {
        val intent = AssistantIntentParser.parse(
            """{"action":"stats","months_back":0,"category":"Comida","text":null}""",
            categories,
        )
        assertIs<AssistantIntent.Stats>(intent)
        assertEquals("Comida", intent.category)
        assertEquals(0, intent.monthsBack)
    }

    @Test
    fun `una categoria inventada se descarta`() {
        val intent = AssistantIntentParser.parse(
            """{"action":"stats","category":"Criptomonedas"}""",
            categories,
        )
        assertIs<AssistantIntent.Stats>(intent)
        assertEquals(null, intent.category)
    }

    @Test
    fun `lee una correccion de movimiento`() {
        val intent = AssistantIntentParser.parse(
            """{"action":"update","amount":20.5,"days_ago":1,"description":"Almuerzo","category":"Comida"}""",
            categories,
        )
        assertIs<AssistantIntent.Update>(intent)
        assertEquals(20.5, intent.amount)
        assertEquals(1, intent.daysAgo)
        assertEquals("Almuerzo", intent.description)
        assertEquals("Comida", intent.category)
    }

    @Test
    fun `una comparacion sin precio cae en ayuda`() {
        assertIs<AssistantIntent.Help>(AssistantIntentParser.parse("""{"action":"compare"}"""))
        assertIs<AssistantIntent.Help>(AssistantIntentParser.parse("basura"))
    }

    @Test
    fun `sin modelo la heuristica entiende gastos y divisas`() {
        assertIs<AssistantIntent.Stats>(AssistantIntentParser.heuristic("¿cuánto gasté este mes?"))
        val compare = AssistantIntentParser.heuristic("si me dan 10 % de descuento, ¿pago en divisas por 100?")
        assertIs<AssistantIntent.Compare>(compare)
        assertEquals(10.0, compare.discountPct)
        assertEquals(100.0, compare.usdPrice)
    }

    // --- Matemática de tasas ---

    @Test
    fun `580 a tasa BCV son dolares, no bolivares`() {
        val intent = AssistantIntentParser.heuristic("si pago en divisas son 360 o 580 a tasa bcv")
        assertIs<AssistantIntent.Compare>(intent)
        assertEquals(360.0, intent.usdPrice)
        assertEquals(580.0, intent.bcvUsdPrice)
        assertNull(intent.bsPrice)
    }

    @Test
    fun `el texto del chip se entiende sin modelo`() {
        val intent = AssistantIntentParser.heuristic("360 en divisas o 500 a BCV")
        assertIs<AssistantIntent.Compare>(intent)
        assertEquals(360.0, intent.usdPrice)
        assertEquals(500.0, intent.bcvUsdPrice)
    }

    @Test
    fun `la comparacion que pide el usuario da 444 como equivalente`() {
        // 500 * 889,58 / 1000,51 = 444,56 USD: los 360 en divisas ganan.
        val comparison = compareOptions(
            usdPrice = 360.0,
            bsPrice = 500.0 * 889.58,
            discountPct = 0.0,
            p2pVes = 1000.51,
        )
        assertTrue(comparison.cheaperInUsd)
        assertEquals(444.56, comparison.bsCostUsd, 0.02)
        assertEquals(84.56, comparison.savingUsd, 0.05)
    }

    @Test
    fun `el caso real 360 en divisas contra 500 mil bolivares`() {
        // 500.000 Bs al P2P (1.000) son 500 USD: pagar en divisas sale mejor.
        val comparison = compareOptions(
            usdPrice = 360.0,
            bsPrice = 500_000.0,
            discountPct = 0.0,
            p2pVes = 1_000.0,
        )
        assertTrue(comparison.cheaperInUsd)
        assertEquals(360.0, comparison.usdCostUsd, 0.001)
        assertEquals(500.0, comparison.bsCostUsd, 0.001)
        assertEquals(140.0, comparison.savingUsd, 0.001)
    }

    @Test
    fun `gana bolivares cuando equivalen a menos dolares`() {
        val comparison = compareOptions(
            usdPrice = 360.0,
            bsPrice = 300_000.0,
            discountPct = 0.0,
            p2pVes = 1_000.0,
        )
        assertFalse(comparison.cheaperInUsd)
        assertEquals(300.0, comparison.bsCostUsd, 0.001)
        assertEquals(60.0, comparison.savingUsd, 0.001)
    }

    @Test
    fun `el descuento en divisas cuenta para el lado de las divisas`() {
        val sinDescuento = compareOptions(360.0, 340_000.0, 0.0, 1_000.0)
        assertFalse(sinDescuento.cheaperInUsd)
        val conDescuento = compareOptions(360.0, 340_000.0, 10.0, 1_000.0)
        // 360 - 10 % = 324 USD, por debajo de los 340 USD de la opción en Bs.
        assertTrue(conDescuento.cheaperInUsd)
        assertEquals(324.0, conDescuento.usdCostUsd, 0.001)
    }

    @Test
    fun `dos precios en la heuristica sin modelo`() {
        val intent = AssistantIntentParser.heuristic(
            "si me dicen 360 en divisas o 500 a bcv cual me sale mejor"
        )
        assertIs<AssistantIntent.Compare>(intent)
        assertEquals(360.0, intent.usdPrice)
        // "500 a bcv" es un precio en DÓLARES que se paga en bolívares.
        assertEquals(500.0, intent.bcvUsdPrice)
    }

    @Test
    fun `200 dolares en bolivares contra 150 en efectivo`() {
        // La pregunta principal: el comercio cobra 200 dólares si pagas en
        // bolívares, o 150 en efectivo. El precio en divisas es 150 y los
        // 200 son el precio en dólares cobrado en Bs (a BCV).
        val intent = AssistantIntentParser.heuristic(
            "me dicen que cuesta 200 dólares en bolívares o 150 en efectivo, cuál me sale mejor"
        )
        assertIs<AssistantIntent.Compare>(intent)
        assertEquals(200.0, intent.bcvUsdPrice)
        assertEquals(150.0, intent.usdPrice)
        assertNull(intent.bsPrice)
    }

    @Test
    fun `gastos en bolivares no se confunden con una comparacion`() {
        assertIs<AssistantIntent.Stats>(AssistantIntentParser.heuristic("¿cuánto gasté en bolívares este mes?"))
    }
}
