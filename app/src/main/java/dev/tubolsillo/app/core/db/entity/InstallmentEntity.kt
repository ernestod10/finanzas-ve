package dev.tubolsillo.app.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cuota BNPL (Cashea: pagos quincenales). La deuda = Σ cuotas PENDING. */
@Entity(
    tableName = "installments",
    indices = [Index("accountId")],
)
data class InstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val dueAt: Long,
    val amountMinor: Long,
    val currency: String,
    val status: String,
    val txId: Long? = null,
)
