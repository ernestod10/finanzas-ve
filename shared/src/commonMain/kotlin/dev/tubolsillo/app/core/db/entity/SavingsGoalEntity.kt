package dev.tubolsillo.app.core.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Objetivo de ahorro: una meta con monto y, opcionalmente, fecha límite. */
@Entity(tableName = "savings_goals")
@Serializable
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetMinor: Long,
    val currency: String,
    /** Fecha límite (epoch ms) o null si no tiene. */
    val deadlineAt: Long? = null,
    val colorHex: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
)
