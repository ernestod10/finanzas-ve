@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.tubolsillo.app.domain.reminders

import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.core.db.entity.InstallmentEntity
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.core.time.MonthBuckets
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.domain.model.Institution
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

/** Un pago que se acerca: tarjeta de crédito o cuota de Cashea. */
data class DueAlert(
    /** Clave estable para no repetir el aviso del mismo vencimiento. */
    val key: String,
    val title: String,
    val body: String,
    val dueAt: Long,
    /** 0 = hoy, 1 = mañana… */
    val daysLeft: Int,
)

/**
 * Decide qué avisar. Puro y testeable: recibe las cuentas y las cuotas, y
 * devuelve los avisos de los próximos [horizonDays] días.
 *
 * Se avisa el pago mínimo de una tarjeta (no la deuda entera) y cada cuota
 * pendiente de Cashea, que es lo que hay que pagar de verdad.
 */
object DueReminders {

    fun compute(
        accounts: List<AccountEntity>,
        installments: List<InstallmentEntity>,
        nowMs: Long,
        horizonDays: Int = 3,
    ): List<DueAlert> {
        val today = MonthBuckets.startOfDay(nowMs)
        val horizonMs = horizonDays * DAY_MS
        val alerts = mutableListOf<DueAlert>()

        for (account in accounts) {
            if (!account.isActive) continue
            val kind = AccountKind.fromKey(account.kind)
            if (kind != AccountKind.CREDIT_CARD) continue
            val dueAt = account.creditDueAt ?: continue
            val daysLeft = daysLeft(today, dueAt) ?: continue
            if (daysLeft !in 0..horizonDays) continue
            val amount = account.minPaymentMinor ?: account.lastBalanceMinor?.let { abs(it) }
            val name = account.name.ifBlank { Institution.fromKey(account.institution).displayName }
            alerts.add(
                DueAlert(
                    key = "card:" + account.id + ":" + MonthBuckets.startOfDay(dueAt),
                    title = name + " " + when (daysLeft) {
                        0 -> "vence hoy"
                        1 -> "vence mañana"
                        else -> "vence en " + daysLeft + " días"
                    },
                    body = buildString {
                        if (amount != null) {
                            append(if (account.minPaymentMinor != null) "Pago mínimo " else "Deuda ")
                            append(MoneyFormatter.formatMinor(amount, account.currency))
                            append(" · ")
                        }
                        append("vence el ").append(dayLabel(dueAt))
                    },
                    dueAt = dueAt,
                    daysLeft = daysLeft,
                )
            )
        }

        for (installment in installments) {
            if (!installment.status.equals("PENDING", ignoreCase = true)) continue
            val daysLeft = daysLeft(today, installment.dueAt) ?: continue
            if (daysLeft !in 0..horizonDays) continue
            val account = accounts.firstOrNull { it.id == installment.accountId } ?: continue
            val name = account.name.ifBlank { Institution.fromKey(account.institution).displayName }
            alerts.add(
                DueAlert(
                    key = "installment:" + installment.id + ":" + MonthBuckets.startOfDay(installment.dueAt),
                    title = "Cuota de " + name + " " + when (daysLeft) {
                        0 -> "vence hoy"
                        1 -> "vence mañana"
                        else -> "vence en " + daysLeft + " días"
                    },
                    body = MoneyFormatter.formatMinor(installment.amountMinor, installment.currency) +
                        " · vence el " + dayLabel(installment.dueAt),
                    dueAt = installment.dueAt,
                    daysLeft = daysLeft,
                )
            )
        }

        return alerts.sortedBy { it.dueAt }
    }

    /** Días de diferencia por día de calendario; null si ya pasó. */
    private fun daysLeft(todayMs: Long, dueAtMs: Long): Int? {
        val dueDay = MonthBuckets.startOfDay(dueAtMs)
        if (dueDay < todayMs) return null
        return ((dueDay - todayMs) / DAY_MS).toInt()
    }

    private fun dayLabel(epochMs: Long): String {
        val date = kotlin.time.Instant.fromEpochMilliseconds(epochMs)
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
        val months = arrayOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
        return date.dayOfMonth.toString() + " " + months[date.monthNumber - 1]
    }

    private const val DAY_MS = 86_400_000L
}
