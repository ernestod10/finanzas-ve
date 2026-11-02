package dev.tubolsillo.app.domain.ai

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parseo de lo que devuelven los modelos: nada de esto debe romper la UI. */
class AiParsingTest {

    private val zone = TimeZone.of("America/Caracas")

    // --- Filtro de búsqueda ---

    @Test
    fun `traduce una pregunta con categoria y fechas`() {
        val filter = SearchQueryParser.parse(
            """{"text":"farmacia","category":"Salud","from":"2026-09-01","to":"2026-09-30","income":false}""",
            listOf("Comida", "Salud", "Transporte"),
            zone,
        )
        assertNotNull(filter)
        assertEquals("farmacia", filter.text)
        assertEquals("Salud", filter.categoryName)
        assertNotNull(filter.fromMs)
        assertNotNull(filter.toMs)
        assertTrue(filter.toMs!! > filter.fromMs!!)
        assertEquals(false, filter.income)
    }

    @Test
    fun `una categoria que no existe se descarta`() {
        val filter = SearchQueryParser.parse(
            """{"category":"Criptomonedas","income":true}""",
            listOf("Comida", "Salud"),
            zone,
        )
        assertNotNull(filter)
        assertNull(filter.categoryName)
        assertEquals(true, filter.income)
    }

    @Test
    fun `acepta el json envuelto en markdown`() {
        val filter = SearchQueryParser.parse(
            "Claro:\n```json\n{\"text\":\"taxi\"}\n```",
            emptyList(),
            zone,
        )
        assertEquals("taxi", filter?.text)
    }

    @Test
    fun `sin nada utilizable no hay filtro`() {
        assertNull(SearchQueryParser.parse(null, emptyList(), zone))
        assertNull(SearchQueryParser.parse("no hay json", emptyList(), zone))
        assertNull(SearchQueryParser.parse("{}", emptyList(), zone))
    }

    @Test
    fun `el texto del filtro se describe para mostrarlo`() {
        val filter = SearchFilter(text = "farmacia", categoryName = "Salud", income = false)
        assertTrue(filter.describe().contains("gastos"))
        assertTrue(filter.describe().contains("Salud"))
    }

    // --- Mapeo de columnas de CSV ---

    @Test
    fun `mapea columnas y descarta indices fuera de rango`() {
        val mapping = CsvMappingParser.parse(
            """{"date":0,"description":1,"amount":2,"debit":null,"credit":null}""",
            columnCount = 3,
        )
        assertNotNull(mapping)
        assertEquals(0, mapping.date)
        assertEquals(1, mapping.description)
        assertEquals(2, mapping.amount)

        val outOfRange = CsvMappingParser.parse("""{"date":9,"amount":2}""", columnCount = 3)
        assertNull(outOfRange)
    }

    @Test
    fun `un mapeo sin fecha no sirve`() {
        assertNull(CsvMappingParser.parse("""{"description":1,"amount":2}""", columnCount = 3))
        assertNull(CsvMappingParser.parse("""{"date":0,"description":1}""", columnCount = 3))
    }

    // --- Lectura de capturas ---

    @Test
    fun `lee el monto aunque venga como texto con coma`() {
        val reading = AiResponseParser.reading(
            """{"is_transaction":true,"amount":"1.234,56","currency":"ves","merchant":"FARMACIA X"}"""
        )
        assertNotNull(reading)
        assertEquals(1234.56, reading.amount)
        assertEquals("VES", reading.currency)
        assertEquals("FARMACIA X", reading.merchant)
        assertEquals(true, reading.isTransaction)
    }

    @Test
    fun `entiende booleanos y dias como texto`() {
        val reading = AiResponseParser.reading(
            """{"is_transaction":"false","day_offset":"-1","last4":"**1234"}"""
        )
        assertNotNull(reading)
        assertEquals(false, reading.isTransaction)
        assertEquals(-1, reading.dayOffset)
        assertEquals("1234", reading.last4)
        assertTrue(reading.isNoise)
    }

    @Test
    fun `sin json no inventa una lectura`() {
        assertNull(AiResponseParser.reading(null))
        assertNull(AiResponseParser.reading("no entendí"))
    }

    // --- Catálogo de modelos locales ---

    @Test
    fun `recomienda el modelo mas capaz que aguanta la RAM`() {
        assertEquals(LocalModelCatalog.QWEN_0_5B.id, LocalModelCatalog.recommend(3.0).id)
        assertEquals(LocalModelCatalog.QWEN_0_5B.id, LocalModelCatalog.recommend(4.0).id)
        assertEquals(LocalModelCatalog.GEMMA_3_1B.id, LocalModelCatalog.recommend(6.0).id)
        assertEquals(LocalModelCatalog.QWEN_1_5B.id, LocalModelCatalog.recommend(8.0).id)
        assertEquals(LocalModelCatalog.QWEN_1_5B.id, LocalModelCatalog.recommend(16.0).id)
    }

    @Test
    fun `un telefono muy justo igual recibe el modelo mas liviano`() {
        assertEquals(LocalModelCatalog.QWEN_0_5B.id, LocalModelCatalog.recommend(2.0).id)
    }

    @Test
    fun `compatible filtra por RAM`() {
        val enCuatroGb = LocalModelCatalog.compatible(4.0)
        assertTrue(enCuatroGb.any { it.id == LocalModelCatalog.QWEN_0_5B.id })
        assertFalse(enCuatroGb.any { it.id == LocalModelCatalog.QWEN_1_5B.id })
    }

    @Test
    fun `byId encuentra el modelo y tolera ids desconocidos`() {
        assertEquals(LocalModelCatalog.GEMMA_3_1B, LocalModelCatalog.byId("gemma3-1b"))
        assertNull(LocalModelCatalog.byId("no-existe"))
        assertNull(LocalModelCatalog.byId(null))
    }
}
