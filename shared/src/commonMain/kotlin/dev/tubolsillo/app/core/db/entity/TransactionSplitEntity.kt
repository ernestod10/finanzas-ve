package dev.tubolsillo.app.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Una parte de un gasto repartida en otra categoría. El súper puede ser
 * "Comida" en el movimiento y a la vez comida + limpieza + mascotas en el
 * presupuesto. La suma de las partes es el monto de la transacción.
 */
@Entity(
    tableName = "transaction_splits",
    indices = [Index("txId")],
)
@Serializable
data class TransactionSplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txId: Long,
    /** Categoría de usuario; null = sin categoría (se suma a "Sin categoría"). */
    val categoryId: Long? = null,
    /** Monto de esta parte, siempre positivo, en la moneda de la transacción. */
    val amountMinor: Long,
)
