package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.dao.TransactionDao

/**
 * Rellena y corrige el equivalente USD congelado de los movimientos viejos:
 *
 *  - filas sin valor (`null`, las que escribían portal/CSV/notificaciones),
 *  - filas en crypto con el valor inflado por el bug de decimales (×10⁶).
 *
 * Se apoya en [UsdEquivalence], que usa la tasa BCV del día de cada movimiento.
 * Es idempotente: correrlo dos veces no cambia nada.
 */
class UsdBackfill(
    private val transactionDao: TransactionDao,
    private val usdEquivalence: UsdEquivalence,
) {
    suspend fun run(batch: Int = 400) {
        transactionDao.getMissingUsd(batch).forEach { tx ->
            val usd = usdEquivalence.usdMinor(tx.amountMinor, tx.currency, tx.occurredAt) ?: return@forEach
            transactionDao.updateUsdEquivalent(tx.id, usd)
        }
        transactionDao.getNonFiatWithUsd(batch).forEach { tx ->
            val usd = usdEquivalence.usdMinor(tx.amountMinor, tx.currency, tx.occurredAt) ?: return@forEach
            if (usd != tx.usdEquivalentMinor) transactionDao.updateUsdEquivalent(tx.id, usd)
        }
    }
}
