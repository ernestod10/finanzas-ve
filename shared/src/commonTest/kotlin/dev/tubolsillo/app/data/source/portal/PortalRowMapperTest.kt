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
    fun `recibida y enviada se distinguen sin columnas`() {
        val recipe = base.copy(signPolicy = SignPolicy.HINTS_FROM_DESCRIPTION)
        val recibida = PortalRowMapper.mapRow(
            PortalRawRow(
                date = "15/08/2026",
                description = "TRANSFERENCIA RECIBIDA",
                amount = "1.000,00",
            ),
            recipe,
            0L,
        )
        val enviada = PortalRowMapper.mapRow(
            PortalRawRow(
                date = "15/08/2026",
                description = "TRANSFERENCIA ENVIADA",
                amount = "1.000,00",
            ),
            recipe,
            0L,
        )
        assertEquals(100000L, recibida?.amountMinor)
        assertEquals(-100000L, enviada?.amountMinor)
    }

    @Test
    fun `un recibo de pago no se confunde con un ingreso`() {
        val recipe = base.copy(signPolicy = SignPolicy.HINTS_FROM_DESCRIPTION)
        val tx = PortalRowMapper.mapRow(
            PortalRawRow(date = "15/08/2026", description = "RECIBO DE PAGO SERVICIO", amount = "50,00"),
            recipe,
            0L,
        )
        assertEquals(-5000L, tx?.amountMinor)
    }

    @Test
    fun `sin columnas, el saldo corrido decide el signo`() {
        // Filas del más nuevo al más viejo; el saldo sube en el abono y baja en el cargo.
        val payload = PortalPayload(
            rows = listOf(
                PortalRawRow(
                    date = "15/08/2026", description = "SIN MARCA",
                    amount = "300,00", balance = "900,00",
                ),
                PortalRawRow(
                    date = "14/08/2026", description = "SIN MARCA",
                    amount = "200,00", balance = "1.200,00",
                ),
                PortalRawRow(
                    date = "13/08/2026", description = "SIN MARCA",
                    amount = "1.000,00", balance = "1.000,00",
                ),
            )
        )
        val mapped = PortalRowMapper.map(payload, base, 0L)
        assertEquals(-30000L, mapped.rows[0].amountMinor) // 900 - 1.200
        assertEquals(20000L, mapped.rows[1].amountMinor) // 1.200 - 1.000
    }

    @Test
    fun `el saldo corrido manda sobre una marca ambigua`() {
        // La fila dice "10,00" sin columna: el saldo dice que entró.
        val payload = PortalPayload(
            rows = listOf(
                PortalRawRow(
                    date = "15/08/2026", description = "COMPRA DUDOSA",
                    amount = "10,00", balance = "510,00",
                ),
                PortalRawRow(
                    date = "14/08/2026", description = "SALDO INICIAL",
                    amount = "500,00", balance = "500,00",
                ),
            )
        )
        val mapped = PortalRowMapper.map(payload, base, 0L)
        assertEquals(1000L, mapped.rows[0].amountMinor)
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

    @Test
    fun `BNC manda la tarjeta dos veces y se unen en una`() {
        // Lectura real del portal de BNC: el bloque de la tarjeta con máscara y
        // un panel de detalle sin máscara con mínimo y vencimiento.
        val payload = PortalPayload(
            accounts = listOf(
                PortalAccountBalance(
                    kind = "CREDIT_CARD",
                    currency = "VES",
                    mask = "***0008",
                    label = "TDC_AVAILABLE",
                    rawText = "Tarjetas de Crédito MasterCard Gold-SOY TECHNO BNC ***0008 " +
                        "Disponible (Bs.): 114.311,14",
                ),
                PortalAccountBalance(
                    kind = "CREDIT_CARD",
                    currency = "VES",
                    label = "TDC_AVAILABLE",
                    rawText = "MasterCard Gold-SOY TECHNO BNC Disponible (Bs.): 114.311,14 " +
                        "Pago mínimo Bs. 0,00 Vence 27/10/2026",
                ),
            ),
        )
        val mapped = PortalRowMapper.map(payload, base, 0L)
        assertEquals(1, mapped.accountBalances.size)
        val card = mapped.accountBalances.single()
        assertEquals("***0008", card.mask)
        assertEquals(11431114L, card.availableMinor)
        assertNull(card.debtMinor)
        // "Pago mínimo 0,00" no es un dato útil.
        assertNull(card.minPaymentMinor)
    }

    @Test
    fun `tarjeta con deuda y disponible trae limite, minimo y vencimiento`() {
        val payload = PortalPayload(
            accounts = listOf(
                PortalAccountBalance(
                    kind = "CREDIT_CARD",
                    currency = "VES",
                    label = "TDC_DEBT",
                    rawText = "Visa Avant Estand 4812***1901 Paga antes del 15-11-2026 " +
                        "Disp. Bs. 2.467,45 Deuda Bs. 22.232,55 Pago min. Bs. 3.705,43",
                ),
            ),
        )
        val card = PortalRowMapper.map(payload, base, 0L).accountBalances.single()
        assertEquals(-2223255L, card.amountMinor)
        assertEquals(2223255L, card.debtMinor)
        assertEquals(246745L, card.availableMinor)
        assertEquals(2470000L, card.limitMinor)
        assertEquals(370543L, card.minPaymentMinor)
        assertNotNull(card.dueAt)
        assertEquals("4812***1901", card.mask)
    }

    @Test
    fun `cuentas y tarjetas en payload se mapean con signo correcto`() {
        val payload = PortalPayload(
            accounts = listOf(
                PortalAccountBalance(kind = "CHECKING", currency = "VES", amountText = "1.414,57", mask = "***4908"),
                PortalAccountBalance(kind = "CREDIT_CARD", currency = "VES", amountText = "67.705,01", mask = "***0008", label = "TDC_AVAILABLE"),
            )
        )
        val mapped = PortalRowMapper.map(payload, base, 0L)
        assertEquals(2, mapped.accountBalances.size)
        assertEquals(141457L, mapped.accountBalances[0].amountMinor)
        assertEquals(-6770501L, mapped.accountBalances[1].amountMinor)
    }
}
