package dev.tubolsillo.app.data.source.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class CsvImporterTest {

    private val zone = ZoneId.of("America/Caracas")

    @Test
    fun `fecha ISO`() {
        val parsed = CsvImporter.parseDate("2026-08-15T10:30:00", null, zone)
        val expected = java.time.LocalDateTime.of(2026, 8, 15, 10, 30)
            .atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, parsed)
    }

    @Test
    fun `fecha con patron`() {
        val parsed = CsvImporter.parseDate("15/08/2026", "dd/MM/yyyy", zone)
        assertNotNull(parsed)
        // 2026-08-15 00:00 en Caracas = 2026-08-15 04:00 UTC
        val expected = java.time.LocalDate.of(2026, 8, 15)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, parsed!!)
    }

    @Test
    fun `epoch millis y segundos`() {
        assertEquals(1_600_000_000_000L, CsvImporter.parseDate("1600000000000", null, zone))
        assertEquals(1_600_000_000_000L, CsvImporter.parseDate("1600000000", null, zone))
    }

    @Test
    fun `fecha invalida da null`() {
        assertNull(CsvImporter.parseDate("no-es-fecha", null, zone))
        assertNull(CsvImporter.parseDate("", null, zone))
    }

    @Test
    fun `parsea filas con template binance`() {
        val csv = """
            Date(UTC),Coin,Network,Amount,TxId
            2026-08-01 10:00:00,USDT,TRC20,100.50,abc123
            2026-08-02 11:00:00,BTC,BTC,0.001,def456
        """.trimIndent()
        val rows = CsvImporter.companionParseRows(csv, CsvPresets.BINANCE_DEPOSITS)
        assertEquals(2, rows.size)
        assertEquals("TRC20", rows[0].description)
        assertEquals(10050L, rows[0].amountMinor)
        assertEquals("USDT", rows[0].currency)
        assertEquals(100000L, rows[1].amountMinor) // 0.001 BTC = 100000 minor (8dp)
        assertEquals("BTC", rows[1].currency)
    }
}

