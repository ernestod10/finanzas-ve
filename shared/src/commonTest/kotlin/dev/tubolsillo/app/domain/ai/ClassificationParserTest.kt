package dev.tubolsillo.app.domain.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassificationParserTest {

    private val categories = listOf("Comida", "Transporte", "Salud")

    @Test
    fun `lee las propuestas del modelo`() {
        val map = ClassificationParser.parse(
            """[{"i":0,"category":"Comida"},{"i":1,"category":"Transporte"}]""",
            3,
            categories,
        )
        assertEquals("Comida", map[0])
        assertEquals("Transporte", map[1])
        assertEquals(null, map[2])
    }

    @Test
    fun `descarta categorias inventadas`() {
        val map = ClassificationParser.parse(
            """[{"i":0,"category":"Criptomonedas"},{"i":1,"category":"salud"}]""",
            2,
            categories,
        )
        assertTrue(map.isEmpty() || map[1] == "Salud")
        assertEquals("Salud", map[1])
        assertEquals(null, map[0])
    }

    @Test
    fun `tolera texto alrededor del arreglo`() {
        val map = ClassificationParser.parse(
            "Claro:\n```json\n[{\"i\":0,\"category\":\"Comida\"}]\n```",
            1,
            categories,
        )
        assertEquals("Comida", map[0])
    }

    @Test
    fun `sin respuesta o basura no propone nada`() {
        assertTrue(ClassificationParser.parse(null, 3, categories).isEmpty())
        assertTrue(ClassificationParser.parse("no sé", 3, categories).isEmpty())
        assertTrue(ClassificationParser.parse("""[{"i":9,"category":"Comida"}]""", 3, categories).isEmpty())
    }
}
