package dev.tubolsillo.app.core.db.entity

import kotlinx.serialization.Serializable

import androidx.room.Entity

/** Relación muchos-a-muchos transacción ↔ etiqueta. */
@Entity(tableName = "transaction_tags", primaryKeys = ["transactionId", "tagId"])
@Serializable
data class TransactionTagCrossRef(
    val transactionId: Long,
    val tagId: Long,
)
