package dev.tubolsillo.app.core.db.entity

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Saldo de una cuenta en un instante, en moneda nativa (negativo = deuda TDC/BNPL).
 * El saldo actual de una cuenta es su snapshot más reciente; la lista de
 * transacciones sigue siendo la fuente de verdad del libro.
 */
@Entity(
    tableName = "balance_snapshots",
    indices = [Index("accountId"), Index("capturedAt")],
)
@Serializable
data class BalanceSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val capturedAt: Long,
    val amountMinor: Long,
    val currency: String,
    val source: String,
    val rawText: String? = null,
)
