package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.dao.CategoryRuleDao
import dev.tubolsillo.app.core.db.entity.CategoryRuleEntity
import dev.tubolsillo.app.core.text.MerchantNormalizer
import dev.tubolsillo.app.domain.model.TransactionCategory
import kotlinx.coroutines.flow.Flow

/**
 * Auto-etiquetado por comercio: el usuario etiqueta "FARMACIA X" como Salud y
 * se crea una regla; las próximas transacciones de ese comercio se etiquetan solas.
 * La coincidencia es substring del comercio normalizado (normalizedMerchant.contains(pattern)).
 */
class RuleEngine(
    private val ruleDao: CategoryRuleDao,
) {

    fun observeRules(): Flow<List<CategoryRuleEntity>> = ruleDao.observeAll()

    /** Regla que aplica para el comercio, o null. */
    suspend fun match(normalizedMerchant: String): CategoryRuleEntity? {
        if (normalizedMerchant.isBlank()) return null
        return ruleDao.getAll().firstOrNull { rule ->
            normalizedMerchant.contains(rule.merchantPattern)
        }
    }

    /** Crea (o refresca) una regla desde una corrección del usuario. */
    suspend fun learn(merchant: String, tagName: String, category: TransactionCategory? = null) {
        val pattern = MerchantNormalizer.normalize(merchant)
        if (pattern.isBlank()) return
        ruleDao.upsert(
            CategoryRuleEntity(
                merchantPattern = pattern,
                tagName = tagName,
                categoryKey = category?.key,
            )
        )
    }

    suspend fun delete(rule: CategoryRuleEntity) = ruleDao.delete(rule)
}
