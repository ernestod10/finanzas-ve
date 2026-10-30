package dev.tubolsillo.app.core.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import dev.tubolsillo.app.core.db.entity.TagEntity
import dev.tubolsillo.app.core.db.entity.TransactionEntity
import dev.tubolsillo.app.core.db.entity.TransactionTagCrossRef
import kotlinx.coroutines.flow.Flow

/** Transacción con sus etiquetas resueltas. */
data class TransactionWithTags(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TransactionTagCrossRef::class,
            parentColumn = "transactionId",
            entityColumn = "tagId",
        )
    )
    val tags: List<TagEntity>,
)

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun getByName(name: String): TagEntity?

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllLinks(links: List<TransactionTagCrossRef>)

    /** Obtiene la etiqueta por nombre, creándola si no existe. */
    @Transaction
    suspend fun getOrCreate(name: String): TagEntity =
        getByName(name) ?: TagEntity(id = insert(TagEntity(name = name)), name = name)

    @Query("INSERT OR IGNORE INTO transaction_tags (transactionId, tagId) VALUES (:transactionId, :tagId)")
    suspend fun link(transactionId: Long, tagId: Long)

    @Query("DELETE FROM transaction_tags WHERE transactionId = :transactionId AND tagId = :tagId")
    suspend fun unlink(transactionId: Long, tagId: Long)

    @Query("SELECT * FROM tags WHERE id IN (SELECT tagId FROM transaction_tags WHERE transactionId = :transactionId)")
    fun observeTagsForTransaction(transactionId: Long): Flow<List<TagEntity>>

    @Query("SELECT * FROM transactions WHERE id IN (SELECT transactionId FROM transaction_tags WHERE tagId = :tagId) ORDER BY occurredAt DESC")
    fun observeTransactionsForTag(tagId: Long): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM tags")
    suspend fun getAllForExport(): List<TagEntity>

    @Query("SELECT * FROM transaction_tags")
    suspend fun getAllLinksForExport(): List<TransactionTagCrossRef>
}
