package dev.tubolsillo.app.core.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Migraciones reales sobre SQLite en memoria (BundledSQLiteDriver, sin
 * emulador). Cada caso crea el esquema de la versión anterior tal como lo
 * generó Room y aplica el ALTER que la app ejecuta al abrir tras actualizarse.
 *
 * No sustituye a `validateMigration` de Room en el dispositivo, pero atrapa el
 * error más común: que el DDL del código y el esquema exportado se separen.
 */
class MigrationTest {

    private fun connection(): SQLiteConnection = BundledSQLiteDriver().open(":memory:")

    private fun SQLiteConnection.exec(sql: String) {
        prepare(sql).use { it.step() }
    }

    private fun SQLiteConnection.columns(table: String): List<String> {
        val out = mutableListOf<String>()
        prepare("PRAGMA table_info(`$table`)").use { st ->
            while (st.step()) out.add(st.getText(1))
        }
        return out
    }

    @Test
    fun `v4 a v5 crea las tablas del plan del mes`() {
        val conn = connection()
        conn.exec(
            "CREATE TABLE `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`institution` TEXT NOT NULL, `name` TEXT NOT NULL, `accountNumberMask` TEXT, " +
                "`currency` TEXT NOT NULL, `kind` TEXT NOT NULL, `creditLimitMinor` INTEGER, " +
                "`colorHex` TEXT, `isActive` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                "`lastBalanceMinor` INTEGER, `lastBalanceAt` INTEGER)"
        )

        TubolsilloDatabase.MIGRATION_4_5.migrate(conn)

        assertEquals(
            listOf("id", "kind", "label", "amountMinor", "currency", "recurrence", "dueAt",
                "dayOfMonth", "startMonth", "endMonth", "categoryId", "isActive", "createdAt", "updatedAt"),
            conn.columns("plan_items"),
        )
        assertTrue(
            conn.columns("plan_item_states")
                .containsAll(listOf("itemId", "month", "status", "txId", "amountMinor", "paidAt")),
        )
    }

    @Test
    fun `v5 a v6 agrega la ficha de la tarjeta sin tocar lo existente`() {
        val conn = connection()
        // Esquema v5 exacto (5.json) con una fila ya guardada.
        conn.exec(
            "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`institution` TEXT NOT NULL, `name` TEXT NOT NULL, `accountNumberMask` TEXT, " +
                "`currency` TEXT NOT NULL, `kind` TEXT NOT NULL, `creditLimitMinor` INTEGER, " +
                "`colorHex` TEXT, `isActive` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                "`lastBalanceMinor` INTEGER, `lastBalanceAt` INTEGER)"
        )
        conn.exec(
            "INSERT INTO accounts (institution, name, currency, kind, creditLimitMinor, isActive, sortOrder, lastBalanceMinor) " +
                "VALUES ('BANCARIBE', 'Bancaribe tdc', 'VES', 'CREDIT_CARD', 2470000, 1, 0, -2223255)"
        )

        TubolsilloDatabase.MIGRATION_5_6.migrate(conn)

        val cols = conn.columns("accounts")
        assertTrue(cols.containsAll(listOf("creditAvailableMinor", "minPaymentMinor", "creditDueAt")))
        // Los datos previos siguen intactos y las columnas nuevas quedan nulas.
        conn.prepare(
            "SELECT name, creditLimitMinor, lastBalanceMinor, creditAvailableMinor, minPaymentMinor, creditDueAt FROM accounts"
        ).use { st ->
            assertTrue(st.step())
            assertEquals("Bancaribe tdc", st.getText(0))
            assertEquals(2470000L, st.getLong(1))
            assertEquals(-2223255L, st.getLong(2))
            assertTrue(st.isNull(3))
            assertTrue(st.isNull(4))
            assertTrue(st.isNull(5))
        }
    }

    @Test
    fun `v6 a v7 deja dedupKey unico y borra duplicados`() {
        val conn = connection()
        conn.exec(
            "CREATE TABLE `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`accountId` INTEGER NOT NULL, `occurredAt` INTEGER NOT NULL, `postedAt` INTEGER, " +
                "`amountMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                "`rawDescription` TEXT, `category` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                "`dedupKey` TEXT, `source` TEXT NOT NULL, `rawJson` TEXT, `transferPairId` INTEGER, " +
                "`isReconciled` INTEGER NOT NULL, `usdEquivalentMinor` INTEGER)"
        )
        conn.exec("CREATE INDEX `index_transactions_dedupKey` ON `transactions` (`dedupKey`)")
        conn.exec(
            "INSERT INTO transactions (accountId, occurredAt, amountMinor, currency, description, category, status, dedupKey, source, isReconciled) " +
                "VALUES (1, 100, -500, 'VES', 'A', 'OTHER', 'CONFIRMED', 'K1', 'SMS', 0)"
        )
        conn.exec(
            "INSERT INTO transactions (accountId, occurredAt, amountMinor, currency, description, category, status, dedupKey, source, isReconciled) " +
                "VALUES (1, 100, -500, 'VES', 'A', 'OTHER', 'CONFIRMED', 'K1', 'PUSH', 0)"
        )
        conn.exec("CREATE TABLE `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `colorHex` TEXT, `sortOrder` INTEGER NOT NULL)")
        conn.exec("CREATE UNIQUE INDEX `index_categories_name` ON `categories` (`name`)")

        TubolsilloDatabase.MIGRATION_6_7.migrate(conn)

        // Quedó una sola fila con la clave duplicada.
        conn.prepare("SELECT COUNT(*) FROM transactions WHERE dedupKey = 'K1'").use { st ->
            assertTrue(st.step())
            assertEquals(1L, st.getLong(0))
        }
        assertTrue(conn.columns("categories").contains("iconKey"))
        // El índice único rechaza insertar otra con la misma clave.
        val failed = runCatching {
            conn.exec(
                "INSERT INTO transactions (accountId, occurredAt, amountMinor, currency, description, category, status, dedupKey, source, isReconciled) " +
                    "VALUES (1, 100, -500, 'VES', 'A', 'OTHER', 'CONFIRMED', 'K1', 'SMS', 0)"
            )
        }.isFailure
        assertTrue(failed)
    }
}
