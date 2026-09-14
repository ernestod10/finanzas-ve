package dev.tubolsillo.app.data.source.portal

import dev.tubolsillo.app.domain.model.Institution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortalJsTest {

    private val recipe = PortalRecipe(institution = Institution.BANESCO, rowsSelector = "table.mov tr")

    @Test
    fun `escapa comillas barras y saltos de linea`() {
        val escaped = PortalJs.escapeJsString("a'b\\c\nd")
        assertFalse(escaped.contains("\n"))
        assertTrue(escaped.contains("\\'"))
        assertTrue(escaped.contains("\\\\"))
    }

    @Test
    fun `el login hace click y no usa form submit como via principal`() {
        val script = PortalJs.buildLoginScript(recipe, "user", "pass")
        assertTrue(script.contains(".click()"))
        // El setter nativo es lo que hace que React/Angular se enteren.
        assertTrue(script.contains("HTMLInputElement.prototype"))
        assertTrue(script.contains("new Event('input'"))
    }

    @Test
    fun `el extractor incrusta el selector de filas y el tope`() {
        val script = PortalJs.buildExtractScript(recipe.copy(maxRows = 42))
        assertTrue(script.contains("table.mov tr"))
        assertTrue(script.contains("42"))
    }

    @Test
    fun `unwrap de resultados de evaluateJavascript`() {
        assertNull(PortalJs.unwrapResult("null"))
        assertNull(PortalJs.unwrapResult(null))
        assertEquals("{\"status\":\"ok\"}", PortalJs.unwrapResult("\"{\\\"status\\\":\\\"ok\\\"}\""))
    }

    @Test
    fun `payload se decodifica con campos ausentes`() {
        val payload = PortalJson.decode("{\"status\":\"ok\",\"rows\":[{\"date\":\"01/09/2026\",\"amount\":\"10,00\"}]}")
        assertEquals(1, payload?.rows?.size)
        assertEquals("", payload?.rows?.first()?.description)
        assertNull(PortalJson.decode("no-json"))
    }
}
