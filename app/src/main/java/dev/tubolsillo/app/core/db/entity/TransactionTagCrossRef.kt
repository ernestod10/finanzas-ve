package dev.tubolsillo.app.core.db.entity

import androidx.room.Entity

/** Relación muchos-a-muchos transacción ↔ etiqueta. */
@Entity(tableName = "transaction_tags", primaryKeys = ["transactionId", "tagId"])
data class TransactionTagCrossRef(
    val transactionId: Long,
    val tagId: Long,
)
