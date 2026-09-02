package dev.tubolsillo.app.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Par de transferencia interna: salida de la cuenta A y entrada en la cuenta B.
 * isConfirmed=false → espera confirmación del usuario en la UI.
 */
@Entity(
    tableName = "transfer_pairs",
    indices = [Index("outTransactionId"), Index("inTransactionId")],
)
data class TransferPairEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val outTransactionId: Long,
    val inTransactionId: Long,
    val matchConfidence: Double,
    val isConfirmed: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
)
