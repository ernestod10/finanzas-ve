package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.db.dao.TagDao
import dev.tubolsillo.app.core.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

class TagRepository(
    private val tagDao: TagDao,
) {

    fun observeAll(): Flow<List<TagEntity>> = tagDao.observeAll()

    fun observeTagsForTransaction(transactionId: Long): Flow<List<TagEntity>> =
        tagDao.observeTagsForTransaction(transactionId)

    /** Etiqueta una transacción (crea la etiqueta si no existe). */
    suspend fun tagTransaction(transactionId: Long, tagName: String) {
        val tag = tagDao.getOrCreate(tagName.trim())
        tagDao.link(transactionId, tag.id)
    }

    suspend fun untagTransaction(transactionId: Long, tagId: Long) =
        tagDao.unlink(transactionId, tagId)
}
