package dev.tubolsillo.app.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.tubolsillo.app.core.db.dao.AccountDao
import dev.tubolsillo.app.core.db.dao.BalanceSnapshotDao
import dev.tubolsillo.app.core.db.dao.BudgetDao
import dev.tubolsillo.app.core.db.dao.CaptureEventDao
import dev.tubolsillo.app.core.db.dao.CategoryRuleDao
import dev.tubolsillo.app.core.db.dao.CurrencyRateDao
import dev.tubolsillo.app.core.db.dao.InstallmentDao
import dev.tubolsillo.app.core.db.dao.TagDao
import dev.tubolsillo.app.core.db.dao.TransactionDao
import dev.tubolsillo.app.core.db.dao.TransferPairDao
import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.core.db.entity.BalanceSnapshotEntity
import dev.tubolsillo.app.core.db.entity.BudgetEntity
import dev.tubolsillo.app.core.db.entity.CaptureEventEntity
import dev.tubolsillo.app.core.db.entity.CategoryRuleEntity
import dev.tubolsillo.app.core.db.entity.CurrencyRateEntity
import dev.tubolsillo.app.core.db.entity.InstallmentEntity
import dev.tubolsillo.app.core.db.entity.TagEntity
import dev.tubolsillo.app.core.db.entity.TransactionEntity
import dev.tubolsillo.app.core.db.entity.TransactionTagCrossRef
import dev.tubolsillo.app.core.db.entity.TransferPairEntity

/** Base de datos local de TuBolsillo. Los esquemas exportados quedan en app/schemas/. */
@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        BalanceSnapshotEntity::class,
        TransferPairEntity::class,
        CurrencyRateEntity::class,
        CaptureEventEntity::class,
        InstallmentEntity::class,
        BudgetEntity::class,
        TagEntity::class,
        TransactionTagCrossRef::class,
        CategoryRuleEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class TubolsilloDatabase : RoomDatabase() {

    companion object {
        /** v1→v2: color personal por cuenta (API del driver KMP). */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                connection.prepare("ALTER TABLE accounts ADD COLUMN colorHex TEXT").use { it.step() }
            }
        }
    }
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun balanceSnapshotDao(): BalanceSnapshotDao
    abstract fun transferPairDao(): TransferPairDao
    abstract fun currencyRateDao(): CurrencyRateDao
    abstract fun captureEventDao(): CaptureEventDao
    abstract fun installmentDao(): InstallmentDao
    abstract fun budgetDao(): BudgetDao
    abstract fun tagDao(): TagDao
    abstract fun categoryRuleDao(): CategoryRuleDao
}
