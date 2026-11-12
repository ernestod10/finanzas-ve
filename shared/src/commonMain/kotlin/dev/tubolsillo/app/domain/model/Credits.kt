package dev.tubolsillo.app.domain.model

import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.core.db.entity.InstallmentEntity
import kotlin.math.abs

/**
 * Una deuda vista desde la pantalla de Créditos: tarjeta, Cashea o cualquier
 * producto con saldo deudor y una fecha en la que hay que pagar.
 */
data class CreditItem(
    val accountId: Long,
    val institutionKey: String,
    val name: String,
    val kind: AccountKind,
    val currency: String,
    /** Deuda total, siempre positiva. */
    val debtMinor: Long,
    val limitMinor: Long? = null,
    /** Crédito disponible (lo publica el banco o se deduce del límite). */
    val availableMinor: Long? = null,
    val minPaymentMinor: Long? = null,
    /** Vencimiento de la tarjeta o fecha de la próxima cuota. */
    val dueAt: Long? = null,
    /** Cuotas pendientes y pagadas (Cashea y compras a cuotas). */
    val pendingInstallments: Int = 0,
    val paidInstallments: Int = 0,
    val nextInstallmentMinor: Long? = null,
    /** true cuando el banco no publica la deuda ni se puede deducir del límite. */
    val debtUnknown: Boolean = false,
) {
    val isCard: Boolean get() = kind == AccountKind.CREDIT_CARD

    /** Lo que hay que pagar en la próxima fecha. */
    val dueAmountMinor: Long? get() = nextInstallmentMinor ?: minPaymentMinor

    val usedPct: Float?
        get() = limitMinor?.takeIf { it > 0 }?.let { (debtMinor.toFloat() / it).coerceIn(0f, 1f) }
}

/** Lo que muestra la pantalla de Créditos. */
data class CreditsOverview(
    val items: List<CreditItem> = emptyList(),
) {
    val isEmpty: Boolean get() = items.isEmpty()

    val cards: List<CreditItem> get() = items.filter { it.kind == AccountKind.CREDIT_CARD }

    /** Cashea y cualquier otro BNPL o compra a cuotas. */
    val installments: List<CreditItem>
        get() = items.filter { it.kind == AccountKind.BNPL || it.pendingInstallments > 0 }

    val otherDebts: List<CreditItem>
        get() = items.filter { it.kind != AccountKind.CREDIT_CARD && it !in installments }

    /** Deuda agrupada por moneda: no se mezclan bolívares con dólares. */
    val debtByCurrency: Map<String, Long>
        get() = items.groupBy { it.currency }
            .mapValues { (_, list) -> list.sumOf { it.debtMinor } }

    /** El pago más cercano de todas las deudas. */
    val nextPayment: CreditItem?
        get() = items.filter { it.dueAt != null }.minByOrNull { it.dueAt!! }
}

/**
 * Arma la vista de créditos a partir de las cuentas de deuda y sus cuotas.
 *
 * Cashea es el caso raro: su cuenta guarda el saldo, pero la verdad está en
 * las cuotas (una cada 14 días). Si hay cuotas pendientes, la deuda y la
 * próxima fecha salen de ellas; si no, se usa el saldo de la cuenta.
 */
object CreditsBuilder {

    fun build(
        accounts: List<AccountEntity>,
        installmentsByAccount: Map<Long, List<InstallmentEntity>>,
    ): CreditsOverview {
        val items = accounts
            .filter { AccountKind.fromKey(it.kind).isDebt && it.isActive }
            .mapNotNull { account -> itemOf(account, installmentsByAccount[account.id].orEmpty()) }
            .sortedWith(compareBy({ it.dueAt == null }, { it.dueAt ?: Long.MAX_VALUE }))
        return CreditsOverview(items)
    }

    private fun itemOf(account: AccountEntity, installments: List<InstallmentEntity>): CreditItem? {
        val kind = AccountKind.fromKey(account.kind)
        val pending = installments.filter { it.status.equals("PENDING", ignoreCase = true) }
        val paid = installments.count { it.status.equals("PAID", ignoreCase = true) }
        val next = pending.minByOrNull { it.dueAt }
        val balanceDebt = account.lastBalanceMinor?.let { abs(it) }
        // BNC (y otros) solo publican el disponible: si se conoce el límite,
        // la deuda sale de ahí en vez de quedarse sin dato.
        val derivedDebt = if (account.creditLimitMinor != null && account.creditAvailableMinor != null) {
            (account.creditLimitMinor - account.creditAvailableMinor).coerceAtLeast(0L)
        } else null

        val debt = when {
            account.currency.isBlank() -> null
            pending.isNotEmpty() -> pending.sumOf { it.amountMinor }
            // Si el saldo guardado es cero pero el banco publica disponible y
            // límite, la deuda real es la diferencia: con el saldo en 0 la
            // tarjeta de BNC (y cualquier portal que solo reporte disponible)
            // desaparecía de Deudas.
            else -> balanceDebt?.takeIf { it > 0L } ?: derivedDebt
        }
        // BNC publica solo el disponible: la tarjeta se muestra igual en Deudas,
        // con la deuda en blanco y el botón para poner el límite.
        val debtUnknown = debt == null
        val shownDebt = debt ?: 0L
        if (debtUnknown) {
            val hasAvailable = account.creditAvailableMinor != null ||
                account.creditLimitMinor != null
            if (kind != AccountKind.CREDIT_CARD || !hasAvailable) return null
        }
        if (debt == 0L && next == null) return null

        val available = account.creditAvailableMinor
            ?: account.creditLimitMinor?.let { limit -> (limit - shownDebt).coerceAtLeast(0L) }

        return CreditItem(
            accountId = account.id,
            institutionKey = account.institution,
            name = account.name,
            kind = kind,
            currency = account.currency,
            debtMinor = shownDebt,
            debtUnknown = debtUnknown,
            limitMinor = account.creditLimitMinor,
            availableMinor = available,
            minPaymentMinor = account.minPaymentMinor,
            dueAt = account.creditDueAt ?: next?.dueAt,
            pendingInstallments = pending.size,
            paidInstallments = paid,
            nextInstallmentMinor = next?.amountMinor,
        )
    }
}
