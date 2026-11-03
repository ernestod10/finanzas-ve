@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.tubolsillo.app.domain.reminders

import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.core.db.entity.InstallmentEntity
import dev.tubolsillo.app.domain.model.AccountKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DueRemindersTest {

    private fun day(year: Int, month: Int, day: Int): Long =
        LocalDate(year, month, day).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

    private fun card(dueAt: Long?, minPayment: Long? = 370_543, balance: Long = -2_223_255) =
        AccountEntity(
            id = 1,
            institution = "BANCARIBE",
            name = "Visa Avant",
            currency = "VES",
            kind = AccountKind.CREDIT_CARD.key,
            lastBalanceMinor = balance,
            minPaymentMinor = minPayment,
            creditDueAt = dueAt,
        )

    private fun installment(id: Long, dueAt: Long, status: String = "PENDING") =
        InstallmentEntity(
            id = id,
            accountId = 2,
            dueAt = dueAt,
            amountMinor = 500_000,
            currency = "VES",
            status = status,
        )

    private val cashea = AccountEntity(
        id = 2,
        institution = "CASHEA",
        name = "Cashea",
        currency = "VES",
        kind = AccountKind.BNPL.key,
        lastBalanceMinor = -1_000_000,
    )

    @Test
    fun `avisa la tarjeta que vence manana`() {
        val now = day(2026, 11, 14)
        val alerts = DueReminders.compute(listOf(card(day(2026, 11, 15))), emptyList(), now)
        assertEquals(1, alerts.size)
        assertEquals(1, alerts.first().daysLeft)
        assertTrue(alerts.first().title.contains("mañana"))
        assertTrue(alerts.first().body.contains("Pago mínimo"))
    }

    @Test
    fun `avisa la cuota de cashea que vence hoy`() {
        val now = day(2026, 11, 15)
        val alerts = DueReminders.compute(
            listOf(cashea),
            listOf(installment(7, day(2026, 11, 15))),
            now,
        )
        assertEquals(1, alerts.size)
        assertEquals(0, alerts.first().daysLeft)
        assertTrue(alerts.first().title.contains("vence hoy"))
    }

    @Test
    fun `ignora lo que vence lejos, lo pagado y lo vencido`() {
        val now = day(2026, 11, 1)
        val alerts = DueReminders.compute(
            listOf(cashea),
            listOf(
                installment(1, day(2026, 11, 20)),
                installment(2, day(2026, 11, 2), "PAID"),
                installment(3, day(2026, 10, 30)),
            ),
            now,
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `sin fecha de vencimiento no hay aviso`() {
        val now = day(2026, 11, 14)
        assertTrue(DueReminders.compute(listOf(card(null)), emptyList(), now).isEmpty())
    }

    @Test
    fun `ordena por fecha y distingue tarjeta de cuota`() {
        val now = day(2026, 11, 14)
        val alerts = DueReminders.compute(
            listOf(card(day(2026, 11, 17)), cashea),
            listOf(installment(9, day(2026, 11, 15))),
            now,
            horizonDays = 5,
        )
        assertEquals(2, alerts.size)
        assertTrue(alerts[0].key.startsWith("installment:"))
        assertTrue(alerts[1].key.startsWith("card:"))
    }
}
