package dev.tubolsillo.app.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Aporte a un objetivo. El avance es la suma de sus aportes. */
@Entity(
    tableName = "savings_contributions",
    indices = [Index("goalId")],
)
@Serializable
data class SavingsContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val amountMinor: Long,
    val contributedAt: Long,
    /** Movimiento con el que se registró el aporte, si lo hubo. */
    val txId: Long? = null,
)
