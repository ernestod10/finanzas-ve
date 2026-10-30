package dev.tubolsillo.app.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.tubolsillo.app.core.db.entity.CategoryEntity
import dev.tubolsillo.app.core.db.dao.CategoryDao
import dev.tubolsillo.app.core.db.dao.AccountDao
import dev.tubolsillo.app.core.db.dao.BalanceSnapshotDao
import dev.tubolsillo.app.core.db.dao.BudgetDao
import dev.tubolsillo.app.core.db.dao.CaptureEventDao
import dev.tubolsillo.app.core.db.dao.CategoryRuleDao
import dev.tubolsillo.app.core.db.dao.CurrencyRateDao
import dev.tubolsillo.app.core.db.dao.InstallmentDao
import dev.tubolsillo.app.core.db.dao.PlanItemDao
import dev.tubolsillo.app.core.db.dao.PlanItemStateDao
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
import dev.tubolsillo.app.core.db.entity.PlanItemEntity
import dev.tubolsillo.app.core.db.entity.PlanItemStateEntity
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
        CategoryEntity::class,
        PlanItemEntity::class,
        PlanItemStateEntity::class,
    ],
    version = 6,
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

        /** v2→v3: equivalente en USD congelado por transacción. */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                connection.prepare("ALTER TABLE transactions ADD COLUMN usdEquivalentMinor INTEGER").use { it.step() }
            }
        }

        /** v3→v4: categorías de gasto (presupuesto) + categoryId en transacciones. */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                // Recrea limpia por si una versión previa dejó la tabla a medias.
                connection.prepare("DROP TABLE IF EXISTS categories").use { it.step() }
                connection.prepare(
                    "CREATE TABLE IF NOT EXISTS categories (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, colorHex TEXT, sortOrder INTEGER NOT NULL)"
                ).use { it.step() }
                connection.prepare(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name ON categories (name)"
                ).use { it.step() }
                val presets = listOf(
                    "Comida|#2E5E43|0", "Transporte|#1E6FA3|1", "Salud|#A63D36|2",
                    "Gimnasio|#7A3FA0|3", "Entretenimiento|#8A5A2B|4", "Hogar|#B08A3E|5",
                    "Educación|#3E7C74|6", "Ropa|#9B3E6E|7", "Servicios|#5E5E8C|8",
                    "Viajes|#4F6D9E|9", "Otros|#8A7A2E|10",
                )
                presets.forEachIndexed { i, p ->
                    val parts = p.split("|")
                    connection.prepare(
                        "INSERT OR IGNORE INTO categories (id, name, colorHex, sortOrder) VALUES (?, ?, ?, ?)"
                    ).use { st ->
                        st.bindLong(1, (i + 1).toLong())
                        st.bindText(2, parts[0])
                        st.bindText(3, parts[1])
                        st.bindLong(4, parts[2].toLong())
                        st.step()
                    }
                }
                connection.prepare("ALTER TABLE transactions ADD COLUMN categoryId INTEGER").use { it.step() }
            }
        }

        /**
         * v4→v5: plan del mes (ingresos y gastos estimados) + estado por mes.
         * El DDL replica exactamente lo que genera Room (mismos nombres de
         * índice, sin DEFAULT espurios): Room valida con validateMigration al
         * abrir y lanza si difiere.
         */
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                connection.prepare(
                    "CREATE TABLE IF NOT EXISTS `plan_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`amountMinor` INTEGER NOT NULL, " +
                        "`currency` TEXT NOT NULL, " +
                        "`recurrence` TEXT NOT NULL, " +
                        "`dueAt` INTEGER, " +
                        "`dayOfMonth` INTEGER, " +
                        "`startMonth` TEXT, " +
                        "`endMonth` TEXT, " +
                        "`categoryId` INTEGER, " +
                        "`isActive` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                ).use { it.step() }
                connection.prepare(
                    "CREATE INDEX IF NOT EXISTS `index_plan_items_categoryId` ON `plan_items` (`categoryId`)"
                ).use { it.step() }
                connection.prepare(
                    "CREATE TABLE IF NOT EXISTS `plan_item_states` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`itemId` INTEGER NOT NULL, " +
                        "`month` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`txId` INTEGER, " +
                        "`amountMinor` INTEGER, " +
                        "`paidAt` INTEGER)"
                ).use { it.step() }
                connection.prepare(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_plan_item_states_itemId_month` " +
                        "ON `plan_item_states` (`itemId`, `month`)"
                ).use { it.step() }
            }
        }

        /**
         * v5→v6: ficha de la tarjeta de crédito leída del portal. El límite ya
         * existía; se suman el disponible impreso, el pago mínimo y el día de
         * pago, que antes se perdían al no tener dónde guardarse.
         */
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
                connection.prepare("ALTER TABLE accounts ADD COLUMN creditAvailableMinor INTEGER").use { it.step() }
                connection.prepare("ALTER TABLE accounts ADD COLUMN minPaymentMinor INTEGER").use { it.step() }
                connection.prepare("ALTER TABLE accounts ADD COLUMN creditDueAt INTEGER").use { it.step() }
            }
        }
    }
    abstract fun categoryDao(): CategoryDao

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
    abstract fun planItemDao(): PlanItemDao
    abstract fun planItemStateDao(): PlanItemStateDao
}
