package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.dao.TransactionDao
import dev.tubolsillo.app.core.db.dao.TransferPairDao
import dev.tubolsillo.app.core.db.entity.TransactionEntity
import dev.tubolsillo.app.core.db.entity.TransferPairEntity
import dev.tubolsillo.app.domain.model.TransactionCategory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matchea las dos patas de una transferencia interna (salida de A, entrada en
 * B): signos opuestos, cuentas distintas, |monto| igual dentro de tolerancia
 * (2% cubre comisiones) tras conversión a USD, ventana ±2h.
 *
 * Ambas patas quedan categoría TRANSFER (no cuentan como gasto en presupuesto
 * ni ensucian el patrimonio neto) y enlazadas por transferPairId. Se re-corre
 * con cada insert, así el orden de llegada de las patas no importa.
 */
@Singleton
class TransferMatcher @Inject constructor(
    private val transactionDao: TransactionDao,
    private val transferPairDao: TransferPairDao,
    private val rateProvider: RateProvider,
) {

    suspend fun matchFor(newTx: TransactionEntity) {
        if (newTx.transferPairId != null) return
        if (newTx.amountMinor == 0L) return

        val sign = if (newTx.amountMinor < 0) -1L else 1L
        val from = newTx.occurredAt - WINDOW_MS
        val to = newTx.occurredAt + WINDOW_MS
        val candidates = transactionDao.findMatchCandidates(
            excludeId = newTx.id,
            from = from,
            to = to,
            sign = sign,
        )
        if (candidates.isEmpty()) return

        val newUsd = toUsdAbs(newTx)
        val best = candidates
            .mapNotNull { candidate ->
                val r = ratio(newUsd, toUsdAbs(candidate))
                if (r != null && r <= TOLERANCE) candidate to r else null
            }
            .minByOrNull { it.second }
            ?: return

        val (match, r) = best
        val confidence = baseConfidence(r) + keywordBoost(newTx, match)
        val pairId = transferPairDao.insert(
            TransferPairEntity(
                outTransactionId = if (newTx.amountMinor < 0) newTx.id else match.id,
                inTransactionId = if (newTx.amountMinor < 0) match.id else newTx.id,
                matchConfidence = confidence,
                isConfirmed = confidence >= CONFIRM_THRESHOLD,
            )
        )
        transactionDao.setTransferPair(newTx.id, pairId)
        transactionDao.setTransferPair(match.id, pairId)
        transactionDao.updateCategory(newTx.id, TransactionCategory.TRANSFER.key)
        transactionDao.updateCategory(match.id, TransactionCategory.TRANSFER.key)
    }

    private suspend fun toUsdAbs(tx: TransactionEntity): Double {
        val rate = rateProvider.getRate(tx.currency)
        val major = tx.amountMinor / dev.tubolsillo.app.core.money.Money.pow10(
            dev.tubolsillo.app.core.money.Currency.of(tx.currency).decimals
        )
        return kotlin.math.abs(major * rate)
    }

    companion object {
        const val WINDOW_MS = 2 * 60 * 60 * 1000L // ±2h
        const val TOLERANCE = 0.02 // 2%
        const val CONFIRM_THRESHOLD = 0.7

        // Lógica pura estática (testeable sin DAOs).

        fun ratio(newUsd: Double, candUsd: Double): Double? =
            if (newUsd <= 0 || candUsd <= 0) null
            else kotlin.math.abs(newUsd - candUsd) / kotlin.math.max(newUsd, candUsd)

        fun baseConfidence(r: Double): Double = when {
            r <= 0.001 -> 0.95
            r <= 0.01 -> 0.85
            else -> 0.70
        }

        fun keywordBoost(a: TransactionEntity, b: TransactionEntity): Double {
            val text = (a.description + " " + b.description).lowercase()
            return if (text.contains("transferencia") || text.contains("pago movil") ||
                text.contains("transferencia recibida") || text.contains("traspaso")
            ) 0.05 else 0.0
        }
    }
}
