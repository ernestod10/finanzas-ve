package dev.tubolsillo.app.domain.model

import dev.tubolsillo.app.core.db.entity.AccountEntity
import kotlin.math.abs
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * "Cuánto te queda para gastar": lo que hay en cuentas líquidas menos lo que
 * ya está comprometido este mes (plan pendiente y pagos de tarjeta). Es el
 * número que PocketGuard/EveryDollar ponen al frente; aquí sale de datos que
 * la app ya tiene, sin servidor.
 */
data class SafeToSpend(
    /** Disponible tras reservar lo comprometido, en USD. */
    val freeUsd: Double,
    /** Lo anterior repartido entre los días que quedan del mes. */
    val perDayUsd: Double,
    val daysLeft: Int,
    /** Saldo líquido total en USD. */
    val liquidUsd: Double,
    /** Comprometido: plan pendiente + próximos pagos de tarjeta. */
    val committedUsd: Double,
)

object SafeToSpendCalculator {

    fun compute(
        liquidUsd: Double,
        plan: PlanMonthData?,
        accounts: List<AccountEntity>,
        vesPerDollar: Double,
        nowMs: Long,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): SafeToSpend? {
        val today = kotlin.time.Instant.fromEpochMilliseconds(nowMs)
            .toLocalDateTime(zone)
            .date
        val daysInMonth = if (today.monthNumber == 12) 31 else daysInMonthOf(today.monthNumber, today.year)
        val daysLeft = (daysInMonth - today.dayOfMonth + 1).coerceAtLeast(1)

        val planPending = plan?.expenses
            ?.filter { it.status != PlanStatus.PAID && it.status != PlanStatus.SKIPPED }
            ?.sumOf { it.amountUsd }
            ?: 0.0

        val cardDue = accounts
            .filter { AccountKind.fromKey(it.kind).isDebt && it.isActive }
            .sumOf { account ->
                val due = account.creditDueAt ?: return@sumOf 0.0
                if (due < nowMs || due > nowMs + 31L * 86_400_000L) return@sumOf 0.0
                val minor = account.minPaymentMinor ?: 0L
                val major = abs(minor) / 100.0
                if (account.currency.equals("VES", ignoreCase = true)) major / vesPerDollar else major
            }

        val committed = planPending + cardDue
        if (liquidUsd <= 0.0 && committed <= 0.0) return null
        val free = (liquidUsd - committed).coerceAtLeast(0.0)
        return SafeToSpend(
            freeUsd = free,
            perDayUsd = free / daysLeft,
            daysLeft = daysLeft,
            liquidUsd = liquidUsd,
            committedUsd = committed,
        )
    }

    /** Días del mes sin depender de calendarios de plataforma (bisiesto incluido). */
    private fun daysInMonthOf(month: Int, year: Int): Int = when (month) {
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}
