package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.core.money.Money
import dev.tubolsillo.app.domain.model.AccountBalanceView
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.domain.model.Totals
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calcula activos, deuda y patrimonio neto en USD.
 * Activos = saldos de CHECKING/SAVINGS/CASH/CRYPTO convertidos a USD.
 * Deuda = saldos absolutos de CREDIT_CARD/BNPL convertidos a USD
 * (Cashea se reconcilia contra Σ cuotas pendientes cuando existan cuotas).
 */
@Singleton
class TotalsCalculator @Inject constructor() {

    fun totals(accounts: List<AccountEntity>, rates: Map<String, Double>): Totals {
        var assets = 0.0
        var debt = 0.0
        for (account in accounts) {
            val minor = account.lastBalanceMinor ?: continue
            val usd = usdEquivalent(minor, account.currency, rates)
            if (AccountKind.fromKey(account.kind).isDebt) {
                debt += kotlin.math.abs(usd)
            } else {
                assets += usd
            }
        }
        return Totals(assetsUsd = assets, debtUsd = debt)
    }

    fun toView(account: AccountEntity, rates: Map<String, Double>): AccountBalanceView =
        AccountBalanceView(
            accountId = account.id,
            institutionKey = account.institution,
            name = account.name,
            kindKey = account.kind,
            currencyCode = account.currency,
            balanceMinor = account.lastBalanceMinor,
            creditLimitMinor = account.creditLimitMinor,
            usdEquivalent = account.lastBalanceMinor?.let {
                usdEquivalent(it, account.currency, rates)
            },
            lastUpdateAt = account.lastBalanceAt,
        )

    fun usdEquivalent(minor: Long, currencyCode: String, rates: Map<String, Double>): Double {
        val rate = rates[currencyCode] ?: 1.0
        return minor / Money.pow10(dev.tubolsillo.app.core.money.Currency.of(currencyCode).decimals) * rate
    }
}
