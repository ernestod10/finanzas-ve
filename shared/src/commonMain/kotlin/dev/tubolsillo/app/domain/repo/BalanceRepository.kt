package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.db.dao.BalanceSnapshotDao
import dev.tubolsillo.app.core.db.dao.InstallmentDao
import dev.tubolsillo.app.core.db.entity.BalanceSnapshotEntity
import dev.tubolsillo.app.core.db.entity.InstallmentEntity
import kotlinx.coroutines.flow.Flow

class BalanceRepository(
    private val snapshotDao: BalanceSnapshotDao,
    private val installmentDao: InstallmentDao,
) {

    fun observeLatest(accountId: Long): Flow<BalanceSnapshotEntity?> =
        snapshotDao.observeLatest(accountId)

    fun observeHistory(accountId: Long): Flow<List<BalanceSnapshotEntity>> =
        snapshotDao.observeHistory(accountId)

    fun observeInstallments(accountId: Long): Flow<List<InstallmentEntity>> =
        installmentDao.observeByAccount(accountId)

    fun observePendingInstallments(): Flow<List<InstallmentEntity>> =
        installmentDao.observePending()

    suspend fun addInstallment(
        accountId: Long,
        dueAt: Long,
        amountMinor: Long,
        currencyCode: String,
    ): Long = installmentDao.insert(
        InstallmentEntity(
            accountId = accountId,
            dueAt = dueAt,
            amountMinor = amountMinor,
            currency = currencyCode,
            status = "PENDING",
        )
    )

    suspend fun markInstallmentPaid(id: Long) = installmentDao.markPaid(id)
}
