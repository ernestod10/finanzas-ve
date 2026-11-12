package dev.tubolsillo.app.domain.model

import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.core.db.entity.InstallmentEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreditsBuilderTest {

    private fun account(
        id: Long,
        kind: AccountKind,
        balance: Long?,
        currency: String = "VES",
        limit: Long? = null,
        available: Long? = null,
        minPayment: Long? = null,
        dueAt: Long? = null,
        name: String = "Cuenta " + id,
    ) = AccountEntity(
        id = id,
        institution = Institution.BANCARIBE.key,
        name = name,
        currency = currency,
        kind = kind.key,
        creditLimitMinor = limit,
        lastBalanceMinor = balance,
        creditAvailableMinor = available,
        minPaymentMinor = minPayment,
        creditDueAt = dueAt,
    )

    private fun installment(accountId: Long, dueAt: Long, amount: Long, status: String) =
        InstallmentEntity(
            accountId = accountId,
            dueAt = dueAt,
            amountMinor = amount,
            currency = "VES",
            status = status,
        )

    @Test
    fun `una tarjeta trae deuda, disponible, minimo y vencimiento`() {
        val card = account(
            id = 1,
            kind = AccountKind.CREDIT_CARD,
            balance = -2_223_255,
            limit = 2_470_000,
            available = 246_745,
            minPayment = 370_543,
            dueAt = 1_794_744_000_000,
            name = "Visa Avant",
        )
        val overview = CreditsBuilder.build(listOf(card), emptyMap())
        val item = overview.cards.single()
        assertEquals(2_223_255, item.debtMinor)
        assertEquals(246_745, item.availableMinor)
        assertEquals(370_543, item.minPaymentMinor)
        assertEquals(370_543, item.dueAmountMinor)
        assertEquals(1_794_744_000_000, item.dueAt)
        assertEquals("Visa Avant", item.name)
        assertTrue(item.usedPct!! > 0.89f)
    }

    @Test
    fun `cashea toma la deuda de las cuotas pendientes, no del saldo`() {
        val cashea = account(id = 2, kind = AccountKind.BNPL, balance = -1_500_000, name = "Cashea")
        val installments = listOf(
            installment(2, dueAt = 1_000, amount = 500_000, status = "PAID"),
            installment(2, dueAt = 2_000, amount = 500_000, status = "PENDING"),
            installment(2, dueAt = 3_000, amount = 500_000, status = "PENDING"),
        )
        val item = CreditsBuilder.build(listOf(cashea), mapOf(2L to installments)).items.single()
        assertEquals(1_000_000, item.debtMinor)
        assertEquals(2, item.pendingInstallments)
        assertEquals(1, item.paidInstallments)
        assertEquals(2_000, item.dueAt)
        assertEquals(500_000, item.nextInstallmentMinor)
        assertEquals(500_000, item.dueAmountMinor)
    }

    @Test
    fun `cashea sin cuotas usa el saldo de la cuenta`() {
        val cashea = account(id = 3, kind = AccountKind.BNPL, balance = -900_000)
        val item = CreditsBuilder.build(listOf(cashea), emptyMap()).items.single()
        assertEquals(900_000, item.debtMinor)
        assertEquals(0, item.pendingInstallments)
        assertNull(item.dueAt)
    }

    @Test
    fun `sin saldo pero con limite y disponible la deuda se deduce`() {
        // Caso BNC: el portal solo publica el disponible.
        val card = account(
            id = 9,
            kind = AccountKind.CREDIT_CARD,
            balance = null,
            limit = 10_000_000,
            available = 8_543_114,
        )
        val item = CreditsBuilder.build(listOf(card), emptyMap()).cards.single()
        assertEquals(1_456_886, item.debtMinor)
        assertEquals(8_543_114, item.availableMinor)
    }

    @Test
    fun `con saldo en cero pero disponible menor al limite la deuda sale igual`() {
        // Caso real de BNC: el portal guardó saldo 0 y sí publicó el disponible.
        // Antes la tarjeta desaparecía de Deudas porque 0 se tomaba como deuda.
        val card = account(
            id = 10,
            kind = AccountKind.CREDIT_CARD,
            balance = 0,
            limit = 16_000_000,
            available = 2_006_134,
        )
        val item = CreditsBuilder.build(listOf(card), emptyMap()).cards.single()
        assertEquals(13_993_866, item.debtMinor)
        assertEquals(2_006_134, item.availableMinor)
    }

    @Test
    fun `las cuentas sin deuda no entran`() {
        val checking = account(id = 4, kind = AccountKind.CHECKING, balance = 1_000_000)
        val emptyCard = account(id = 5, kind = AccountKind.CREDIT_CARD, balance = 0)
        assertTrue(CreditsBuilder.build(listOf(checking), emptyMap()).isEmpty)
        assertTrue(CreditsBuilder.build(listOf(emptyCard), emptyMap()).isEmpty)
    }

    @Test
    fun `ordena por fecha y elige el proximo pago`() {
        val card = account(
            id = 1, kind = AccountKind.CREDIT_CARD, balance = -100_000,
            dueAt = 5_000, minPayment = 10_000,
        )
        val cashea = account(id = 2, kind = AccountKind.BNPL, balance = -50_000)
        val installments = listOf(installment(2, dueAt = 1_000, amount = 50_000, status = "PENDING"))
        val overview = CreditsBuilder.build(listOf(card, cashea), mapOf(2L to installments))
        assertEquals(2, overview.items.size)
        assertEquals(1_000, overview.nextPayment?.dueAt)
        assertEquals("Cuenta 2", overview.nextPayment?.name)
        assertEquals(1, overview.installments.size) // solo Cashea tiene cuotas
        assertEquals(1, overview.cards.size)
    }

    @Test
    fun `la deuda total no mezcla monedas`() {
        val vesCard = account(id = 1, kind = AccountKind.CREDIT_CARD, balance = -100_000)
        val usdCard = account(id = 2, kind = AccountKind.CREDIT_CARD, balance = -5_000, currency = "USD")
        val totals = CreditsBuilder.build(listOf(vesCard, usdCard), emptyMap()).debtByCurrency
        assertEquals(100_000, totals["VES"])
        assertEquals(5_000, totals["USD"])
    }
}
