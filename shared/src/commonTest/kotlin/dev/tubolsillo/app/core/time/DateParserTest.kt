package dev.tubolsillo.app.core.time

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DateParserTest {

    private val caracas = TimeZone.of("America/Caracas")

    @Test
    fun `fecha simple con patron dd MM yyyy`() {
        val epoch = DateParser.parse("15/08/2026", "dd/MM/yyyy", caracas)
        assertNotNull(epoch)
    }

    @Test
    fun `fecha con hora exige patron con hora`() {
        val conHora = DateParser.parse("15/08/2026 10:30", "dd/MM/yyyy HH:mm", caracas)
        assertNotNull(conHora)
        // La trampa: si el portal muestra hora y la receta no la declara, la
        // fila se descartaría en silencio. El test la deja documentada.
        assertNull(DateParser.parse("15/08/2026 10:30", "dd/MM/yyyy", caracas))
    }

    @Test
    fun `epoch en segundos y milisegundos`() {
        assertEquals(1_700_000_000_000L, DateParser.parse("1700000000", null, caracas))
        assertEquals(1_700_000_000_123L, DateParser.parse("1700000000123", null, caracas))
    }

    @Test
    fun `texto que no es fecha devuelve null`() {
        assertNull(DateParser.parse("no es fecha", "dd/MM/yyyy", caracas))
        assertNull(DateParser.parse("", "dd/MM/yyyy", caracas))
    }

    @Test
    fun `zona horaria cambia el epoch`() {
        val ccs = DateParser.parse("15/08/2026", "dd/MM/yyyy", caracas)
        val pty = DateParser.parse("15/08/2026", "dd/MM/yyyy", TimeZone.of("America/Panama"))
        assertNotNull(ccs); assertNotNull(pty)
        assertEquals(60 * 60 * 1000L, pty - ccs)
    }
}
