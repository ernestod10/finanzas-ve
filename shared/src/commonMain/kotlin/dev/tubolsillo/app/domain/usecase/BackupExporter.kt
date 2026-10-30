package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.time.nowMillis

import dev.tubolsillo.app.core.db.TubolsilloDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    /** Categorías de gasto: se habían quedado fuera del respaldo. */
    val categories: List<dev.tubolsillo.app.core.db.entity.CategoryEntity> = emptyList(),
    val planItems: List<dev.tubolsillo.app.core.db.entity.PlanItemEntity> = emptyList(),
    val planItemStates: List<dev.tubolsillo.app.core.db.entity.PlanItemStateEntity> = emptyList(),
)

/**
 * Exporta y restaura TODA la base de datos a/desde un JSON para respaldo.
 * Los eventos crudos van incluidos: son el corpus de parsers.
 */
data class BackupRestoreSummary(
    val accounts: Int,
    val transactions: Int,
    val categories: Int,
    val planItems: Int,
)

class BackupExporter(
    private val db: TubolsilloDatabase,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

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
            categories = db.categoryDao().getAll(),
            planItems = db.planItemDao().getAllForExport(),
            planItemStates = db.planItemStateDao().getAllForExport(),
        )
        return json.encodeToString(dto)
    }

    /**
     * Restaura un archivo JSON de respaldo haciendo upsert (REPLACE) por clave
     * primaria/única, preservando las relaciones entre cuentas, categorías,
     * movimientos y plan del mes.
     */
    suspend fun importJson(rawJson: String): BackupRestoreSummary {
        val dto = json.decodeFromString<BackupDto>(rawJson)
        if (dto.categories.isNotEmpty()) db.categoryDao().upsertAll(dto.categories)
        if (dto.accounts.isNotEmpty()) db.accountDao().upsertAll(dto.accounts)
        if (dto.currencyRates.isNotEmpty()) db.currencyRateDao().upsertAll(dto.currencyRates)
        if (dto.tags.isNotEmpty()) db.tagDao().upsertAll(dto.tags)
        if (dto.categoryRules.isNotEmpty()) db.categoryRuleDao().upsertAll(dto.categoryRules)
        if (dto.budgets.isNotEmpty()) db.budgetDao().upsertAll(dto.budgets)
        if (dto.transactions.isNotEmpty()) db.transactionDao().upsertAll(dto.transactions)
        if (dto.tagLinks.isNotEmpty()) db.tagDao().upsertAllLinks(dto.tagLinks)
        if (dto.balanceSnapshots.isNotEmpty()) db.balanceSnapshotDao().upsertAll(dto.balanceSnapshots)
        if (dto.transferPairs.isNotEmpty()) db.transferPairDao().upsertAll(dto.transferPairs)
        if (dto.captureEvents.isNotEmpty()) db.captureEventDao().upsertAll(dto.captureEvents)
        if (dto.installments.isNotEmpty()) db.installmentDao().upsertAll(dto.installments)
        if (dto.planItems.isNotEmpty()) db.planItemDao().upsertAll(dto.planItems)
        if (dto.planItemStates.isNotEmpty()) db.planItemStateDao().upsertAll(dto.planItemStates)
        return BackupRestoreSummary(
            accounts = dto.accounts.size,
            transactions = dto.transactions.size,
            categories = dto.categories.size,
            planItems = dto.planItems.size,
        )
    }
}
