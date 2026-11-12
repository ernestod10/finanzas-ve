package dev.tubolsillo.app.core.db.entity

import dev.tubolsillo.app.core.time.nowMillis

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Transacción. Corazón del esquema:
 *  - amountMinor firmado (compra = negativo) en moneda nativa de la cuenta.
 *  - dedupKey permite que la misma operación llegada por SMS y push cuente una vez.
 *  - transferPairId enlaza las dos patas de una transferencia interna.
 *  - rawJson conserva el payload crudo del conector; el raw nunca se pierde.
 */
@Entity(
    tableName = "transactions",
    indices = [Index("accountId"), Index(value = ["dedupKey"], unique = true), Index("occurredAt")],
)
@Serializable
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val occurredAt: Long,
    val postedAt: Long? = null,
    val amountMinor: Long,
    val currency: String,
    val description: String,
    val rawDescription: String? = null,
    val category: String,
    val status: String,
    val dedupKey: String? = null,
    val source: String,
    val rawJson: String? = null,
    val transferPairId: Long? = null,
    val isReconciled: Boolean = false,
    /** Equivalente en USD de la transacción al momento de ocurrir (fijo). */
    val usdEquivalentMinor: Long? = null,
    /** Categoría de gasto del presupuesto (id de categories), opcional. */
    val categoryId: Long? = null,
    val createdAt: Long = nowMillis(),
    val updatedAt: Long = nowMillis(),
)
