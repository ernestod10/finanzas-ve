package dev.tubolsillo.app.data.source.portal

import dev.tubolsillo.app.domain.model.Institution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortalRowMapperTest {

    private val base = PortalRecipe(
        institution = Institution.BANESCO,
        dateFormat = "dd/MM/yyyy",
        currency = "VES",
        signPolicy = SignPolicy.DEBIT_CREDIT_COLUMNS,
    )

    @Test
    fun `columna debito resta y credito suma`() {
        val debito = PortalRowMapper.mapRow(
            PortalRawRow(date = "15/08/2026", description = "COMPRA", debit = "1.234,56"), base, 0L
        )
        assertEquals(-123456L, debito?.amountMinor)

        val credito = PortalRowMapper.mapRow(
            PortalRawRow(date = "15/08/2026", description = "ABONO", credit = "500,00"), base, 0L
        )
        assertEquals(50000L, credito?.amountMinor)
    }

    @Test
    fun `monto entre parentesis es negativo`() {
        // AmountNormalizer filtra los paréntesis, así que el signo NO puede
        // delegarse en él: lo decide la política de la receta.
        val row = PortalRawRow(date = "15/08/2026", description = "CARGO", amount = "(1.234,56)")
        val tx = PortalRowMapper.mapRow(row, base.copy(signPolicy = SignPolicy.AMOUNT_SIGNED), 0L)
        assertEquals(-123456L, tx?.amountMinor)
    }

    @Test
    fun `formato panameno en dolares`() {
        val recipe = base.copy(
            institution = Institution.MERCANTIL_PA,
            currency = "USD",
            timeZoneId = "America/Panama",
            signPolicy = SignPolicy.HINTS_FROM_DESCRIPTION,
        )
        val tx = PortalRowMapper.mapRow(
            PortalRawRow(date = "15/08/2026", description = "COMPRA SUPER", amount = "1,234.56"), recipe, 0L
        )
        assertEquals(-123456L, tx?.amountMinor)
        assertEquals("USD", tx?.currency)
    }

    @Test
    fun `pistas de descripcion deciden el signo`() {
        val recipe = base.copy(signPolicy = SignPolicy.HINTS_FROM_DESCRIPTION)
        val gasto = PortalRowMapper.mapRow(
            PortalRawRow(date = "15/08/2026", description = "COMPRA POS FARMACIA", amount = "100,00"), recipe, 0L
        )
        val ingreso = PortalRowMapper.mapRow(
            PortalRawRow(date = "15/08/2026", description = "ABONO NOMINA", amount = "100,00"), recipe, 0L
        )
        assertEquals(-10000L, gasto?.amountMinor)
        assertEquals(10000L, ingreso?.amountMinor)
    }

    @Test
    fun `fila sin fecha o sin monto se descarta y se cuenta`() {
        assertNull(PortalRowMapper.mapRow(PortalRawRow(description = "SIN FECHA", amount = "10,00"), base, 0L))
        assertNull(PortalRowMapper.mapRow(PortalRawRow(date = "15/08/2026", description = "SIN MONTO"), base, 0L))

        val payload = PortalPayload(
            rows = listOf(
                PortalRawRow(date = "15/08/2026", description = "OK", debit = "10,00"),
                PortalRawRow(description = "CABECERA"),
            )
        )
        val mapped = PortalRowMapper.map(payload, base, 0L)
        assertEquals(1, mapped.rows.size)
        assertEquals(1, mapped.skipped)
    }

    @Test
    fun `la referencia queda en la descripcion`() {
        val tx = PortalRowMapper.mapRow(
            PortalRawRow(date = "15/08/2026", description = "PAGO", debit = "10,00", reference = "998877"),
            base, 0L,
        )
        assertNotNull(tx)
        assertTrue(tx.description.contains("998877"))
    }

    @Test
    fun `saldo del portal se lee del payload`() {
        val payload = PortalPayload(balanceText = "4.532,10")
        assertEquals(453210L, PortalRowMapper.balanceMinorOf(payload, base))
        assertNull(PortalRowMapper.balanceMinorOf(PortalPayload(), base))
    }
}
