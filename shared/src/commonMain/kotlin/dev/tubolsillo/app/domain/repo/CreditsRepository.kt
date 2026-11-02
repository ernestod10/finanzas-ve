package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.db.dao.InstallmentDao
import dev.tubolsillo.app.domain.model.CreditsBuilder
import dev.tubolsillo.app.domain.model.CreditsOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Deudas vivas: tarjetas de crédito, Cashea y compras a cuotas, con el monto y
 * la próxima fecha de pago. Junta cuentas e installments porque la verdad de
 * Cashea está en sus cuotas, no en el saldo de la cuenta.
 */
class CreditsRepository(
    private val accountRepository: AccountRepository,
    private val installmentDao: InstallmentDao,
) {
    fun observe(): Flow<CreditsOverview> = combine(
        accountRepository.observeActive(),
        installmentDao.observeAll(),
    ) { accounts, installments ->
        CreditsBuilder.build(accounts, installments.groupBy { it.accountId })
    }
        .map { it }
        .flowOn(Dispatchers.Default)
}
