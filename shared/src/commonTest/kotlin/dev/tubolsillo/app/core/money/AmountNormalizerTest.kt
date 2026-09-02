package dev.tubolsillo.app.core.money

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

/**
 * Heurísticas de parseo de montos (la ambigüedad clásica de los mensajes
 * bancarios venezolanos: coma y punto intercambiables).
 */
class AmountNormalizerTest {

    @Test
    fun `VES con ambos separadores - coma decimal`() {
        // "1.234,56" → 123456 minor
        assertEquals(123456L, AmountNormalizer.parseMinor("Bs. 1.234,56", "VES"))
    }

    @Test
    fun `VES con ambos separadores - punto decimal`() {
        // "1,234.56" → 123456 minor
        assertEquals(123456L, AmountNormalizer.parseMinor("1,234.56", "VES"))
    }

    @Test
    fun `dos decimales con coma - montos chicos`() {
        assertEquals(2500L, AmountNormalizer.parseMinor("25,00", "VES"))
        assertEquals(123L, AmountNormalizer.parseMinor("1,23", "VES"))
    }

    @Test
    fun `un separador con tres digitos - miles`() {
        // "1.234" → 1234 major = 123400 minor
        assertEquals(123400L, AmountNormalizer.parseMinor("1.234", "VES"))
        assertEquals(1000000L, AmountNormalizer.parseMinor("10.000", "VES"))
    }

    @Test
    fun `un separador con un digito - decimal`() {
        assertEquals(150L, AmountNormalizer.parseMinor("1,5", "VES"))
    }

    @Test
    fun `entero puro`() {
        assertEquals(250000L, AmountNormalizer.parseMinor("2500", "VES"))
    }

    @Test
    fun `negativo con simbolo`() {
        assertEquals(-2500L, AmountNormalizer.parseMinor("-25,00", "VES"))
        assertEquals(-123456L, AmountNormalizer.parseMinor("-1.234,56", "VES"))
    }

    @Test
    fun `USD con convencion US`() {
        assertEquals(1234L, AmountNormalizer.parseMinor("$12.34", "USD"))
        assertEquals(123456L, AmountNormalizer.parseMinor("1,234.56", "USD"))
    }

    @Test
    fun `crypto 8 decimales`() {
        assertEquals(1234L, AmountNormalizer.parseMinor("0.00001234", "BTC"))
        assertEquals(150000000L, AmountNormalizer.parseMinor("1.5", "BTC"))
    }

    @Test
    fun `texto con letras alrededor`() {
        assertEquals(2500L, AmountNormalizer.parseMinor("Compra por Bs. 25,00 en FARMACIA X", "VES"))
        assertEquals(1234L, AmountNormalizer.parseMinor("B/. 12.34 Mercantil", "USD"))
    }

    @Test
    fun `sin numero devuelve null`() {
        assertNull(AmountNormalizer.parseMinor("hola mundo", "VES"))
        assertNull(AmountNormalizer.parseMinor("", "VES"))
    }

    @Test
    fun `miles repetidos - solo un separador tipo`() {
        // "1.234.567" → 1234567 major
        assertEquals(123456700L, AmountNormalizer.parseMinor("1.234.567", "VES"))
    }

    @Test
    fun `coma miles con decimal ausente`() {
        // "12,345" → miles → 12345 major
        assertEquals(1234500L, AmountNormalizer.parseMinor("12,345", "VES"))
    }
}
