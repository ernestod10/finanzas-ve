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
    private val accountRepository: AccountRepository,
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

    /**
     * Asigna la categoría de presupuesto a una transacción y, si [learnMerchant]
     * es true, aplica automáticamente esa misma categoría a cualquier otro
     * movimiento sin categoría del mismo comercio normalizado.
     */
    suspend fun setUserCategory(id: Long, categoryId: Long?, learnMerchant: Boolean = true) {
        transactionDao.setCategory(id, categoryId)
        if (learnMerchant && categoryId != null) {
            val tx = transactionDao.getById(id) ?: return
            val pattern = MerchantNormalizer.normalize(tx.description)
            if (pattern.length >= 3) {
                val all = transactionDao.getAllForExport()
                for (other in all) {
                    if (other.id != id && other.categoryId == null) {
                        val normOther = MerchantNormalizer.normalize(other.description)
                        if (normOther.isNotBlank() && (normOther.contains(pattern) || pattern.contains(normOther))) {
                            transactionDao.setCategory(other.id, categoryId)
                        }
                    }
                }
            }
        }
    }

    suspend fun updateAmount(id: Long, amountMinor: Long) =
        transactionDao.updateAmount(id, amountMinor)

    suspend fun updateDetails(id: Long, description: String, amountMinor: Long, currencyCode: String) {
        val previous = transactionDao.getById(id) ?: return
        val usdEquiv = usdEquivalent(amountMinor, currencyCode)
        transactionDao.updateDetails(id, description.trim(), amountMinor, usdEquiv)
        // Solo los movimientos manuales mueven el saldo: los capturados por el
        // banco ya vienen reflejados en el saldo que reporta el banco.
        if (previous.source == SourceType.MANUAL.key &&
            previous.amountMinor != amountMinor &&
            previous.currency.equals(currencyCode, ignoreCase = true)
        ) {
            accountRepository.applyTransactionDelta(
                accountId = previous.accountId,
                deltaMinor = amountMinor - previous.amountMinor,
                currencyCode = previous.currency,
            )
        }
    }

    suspend fun delete(id: Long) {
        val previous = transactionDao.getById(id) ?: return
        transactionDao.deleteById(id)
        if (previous.source == SourceType.MANUAL.key) {
            accountRepository.applyTransactionDelta(
                accountId = previous.accountId,
                deltaMinor = -previous.amountMinor,
                currencyCode = previous.currency,
            )
        }
    }
}
