package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.time.nowMillis

import dev.tubolsillo.app.core.db.TubolsilloDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** DTOs de respaldo (JSON plano, sin anotaciones de Room). */
@Serializable
data class BackupDto(
    val version: Int = 1,
    val exportedAt: Long,
    val accounts: List<dev.tubolsillo.app.core.db.entity.AccountEntity>,
    val transactions: List<dev.tubolsillo.app.core.db.entity.TransactionEntity>,
    val balanceSnapshots: List<dev.tubolsillo.app.core.db.entity.BalanceSnapshotEntity>,
    val transferPairs: List<dev.tubolsillo.app.core.db.entity.TransferPairEntity>,
    val currencyRates: List<dev.tubolsillo.app.core.db.entity.CurrencyRateEntity>,
    val captureEvents: List<dev.tubolsillo.app.core.db.entity.CaptureEventEntity>,
    val installments: List<dev.tubolsillo.app.core.db.entity.InstallmentEntity>,
    val budgets: List<dev.tubolsillo.app.core.db.entity.BudgetEntity>,
    val tags: List<dev.tubolsillo.app.core.db.entity.TagEntity>,
    val tagLinks: List<dev.tubolsillo.app.core.db.entity.TransactionTagCrossRef>,
    val categoryRules: List<dev.tubolsillo.app.core.db.entity.CategoryRuleEntity>,
)

/**
 * Exporta TODA la base de datos a un JSON para respaldo (SAF CreateDocument
 * escribe el texto). Los eventos crudos van incluidos: son el corpus de
 * parsers. Restaurar desde este archivo es trabajo futuro.
 */
@Singleton
class BackupExporter @Inject constructor(
    private val db: TubolsilloDatabase,
) {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun exportJson(): String {
        val dto = BackupDto(
            exportedAt = nowMillis(),
            accounts = db.accountDao().getAllForExport(),
            transactions = db.transactionDao().getAllForExport(),
            balanceSnapshots = db.balanceSnapshotDao().getAllForExport(),
            transferPairs = db.transferPairDao().getAllForExport(),
            currencyRates = db.currencyRateDao().getAllForExport(),
            captureEvents = db.captureEventDao().getAllForExport(),
            installments = db.installmentDao().getAllForExport(),
            budgets = db.budgetDao().getAllForExport(),
            tags = db.tagDao().getAllForExport(),
            tagLinks = db.tagDao().getAllLinksForExport(),
            categoryRules = db.categoryRuleDao().getAll(),
        )
        return json.encodeToString(dto)
    }
}
