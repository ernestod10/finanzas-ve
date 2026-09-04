package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.time.nowMillis

import dev.tubolsillo.app.core.db.dao.TagDao
import dev.tubolsillo.app.core.db.dao.TransactionDao
import dev.tubolsillo.app.core.db.dao.TransactionWithTags
import dev.tubolsillo.app.core.db.entity.TransactionEntity
import dev.tubolsillo.app.core.text.MerchantNormalizer
import dev.tubolsillo.app.domain.model.SourceType
import dev.tubolsillo.app.domain.model.TransactionCategory
import dev.tubolsillo.app.domain.model.TxStatus
import dev.tubolsillo.app.domain.usecase.RateProvider
import dev.tubolsillo.app.domain.usecase.RuleEngine
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val tagDao: TagDao,
    private val ruleEngine: RuleEngine,
    private val rateProvider: RateProvider,
) {

    fun observeAll(): Flow<List<TransactionEntity>> = transactionDao.observeAll()

    fun observeAllWithTags(): Flow<List<TransactionWithTags>> =
        transactionDao.observeAllWithTags()

    fun observeRecent(limit: Int): Flow<List<TransactionEntity>> =
        transactionDao.observeRecent(limit)

    fun observeByAccount(accountId: Long): Flow<List<TransactionEntity>> =
        transactionDao.observeByAccount(accountId)

    fun observeWithTagsByAccount(accountId: Long): Flow<List<TransactionWithTags>> =
        transactionDao.observeWithTagsByAccount(accountId)

    fun observeById(id: Long): Flow<TransactionEntity?> = transactionDao.observeById(id)

    suspend fun getById(id: Long): TransactionEntity? = transactionDao.getById(id)

    /**
     * Entrada manual. Signo del monto: gasto = negativo, ingreso = positivo.
     * Las entradas manuales no generan dedupKey (solo las capturas automáticas dedupean).
     * Aplica auto-etiquetado: si existe una regla para el comercio, la transacción
     * nace etiquetada ("FARMACIA X" → Salud) sin intervención del usuario.
     */
    suspend fun insertManual(
        accountId: Long,
        amountMinor: Long,
        currencyCode: String,
        description: String,
        category: TransactionCategory,
        occurredAt: Long = nowMillis(),
        status: TxStatus = TxStatus.CONFIRMED,
    ): Long {
        val usdEquiv = usdEquivalent(amountMinor, currencyCode)
        val id = transactionDao.insert(
            TransactionEntity(
                accountId = accountId,
                occurredAt = occurredAt,
                amountMinor = amountMinor,
                currency = currencyCode,
                description = description,
                rawDescription = description,
                category = category.key,
                status = status.key,
                source = SourceType.MANUAL.key,
                usdEquivalentMinor = usdEquiv,
            )
        )
        applyRules(id, description, category)
        return id
    }

    /**
     * Equivalente en USD congelado al momento de la transacción, con la tasa
     * vigente. Para monedas ya en USD es el mismo monto.
     */
    private suspend fun usdEquivalent(amountMinor: Long, currencyCode: String): Long? =
        if (currencyCode == "USD") amountMinor
        else runCatching {
            Math.round(amountMinor * rateProvider.getRate(currencyCode))
        }.getOrDefault(null)

    /** Aplica las reglas de auto-etiquetado existentes a una transacción. */
    suspend fun applyRules(transactionId: Long, rawDescription: String, defaultCategory: TransactionCategory) {
        val rule = ruleEngine.match(MerchantNormalizer.normalize(rawDescription)) ?: return
        rule.tagName?.let { tagName ->
            tagDao.link(transactionId, tagDao.getOrCreate(tagName).id)
        }
        val effectiveCategory = rule.categoryKey ?: defaultCategory.key
        if (effectiveCategory != defaultCategory.key) {
            transactionDao.updateCategory(transactionId, effectiveCategory)
        }
    }

    suspend fun updateCategory(id: Long, category: TransactionCategory) =
        transactionDao.updateCategory(id, category.key)

    suspend fun setUserCategory(id: Long, categoryId: Long?) =
        transactionDao.setCategory(id, categoryId)

    suspend fun updateAmount(id: Long, amountMinor: Long) =
        transactionDao.updateAmount(id, amountMinor)
}
