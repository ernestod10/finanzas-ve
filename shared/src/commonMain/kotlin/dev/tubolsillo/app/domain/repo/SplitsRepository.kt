package dev.tubolsillo.app.domain.repo

import androidx.room.withTransaction
import dev.tubolsillo.app.core.db.TubolsilloDatabase
import dev.tubolsillo.app.core.db.dao.SplitDao
import dev.tubolsillo.app.core.db.entity.TransactionSplitEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reparto de un gasto en varias categorías. Se guarda todo o nada: primero se
 * borran las partes viejas y se insertan las nuevas en la misma transacción de
 * Room, así un fallo a mitad no deja el movimiento con la mitad de las partes.
 */
class SplitsRepository(
    private val splitDao: SplitDao,
    private val database: TubolsilloDatabase,
) {

    fun observeAll(): Flow<List<TransactionSplitEntity>> = splitDao.observeAll()

    suspend fun setSplits(txId: Long, parts: List<Pair<Long?, Long>>) {
        database.withTransaction {
            splitDao.deleteForTx(txId)
            val valid = parts.filter { it.second > 0 }
            if (valid.isNotEmpty()) {
                splitDao.insertAll(
                    valid.map { (categoryId, amount) ->
                        TransactionSplitEntity(txId = txId, categoryId = categoryId, amountMinor = amount)
                    }
                )
            }
        }
    }

    suspend fun clear(txId: Long) {
        database.withTransaction { splitDao.deleteForTx(txId) }
    }
}
