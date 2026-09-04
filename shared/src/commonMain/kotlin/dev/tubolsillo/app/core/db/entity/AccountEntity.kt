package dev.tubolsillo.app.core.db.entity

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cuenta bancaria / TDC / wallet crypto / BNPL.
 * Los enums se guardan como clave String; lastBalance* es una copia
 * desnormalizada del último snapshot para que el dashboard sea rápido.
 */
@Entity(
    tableName = "accounts",
    indices = [Index("institution")],
)
@Serializable
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val institution: String,
    val name: String,
    val accountNumberMask: String? = null,
    val currency: String,
    val kind: String,
    val creditLimitMinor: Long? = null,
    /** Color personal del usuario en formato #RRGGBB o null = por tipo. */
    val colorHex: String? = null,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val lastBalanceMinor: Long? = null,
    val lastBalanceAt: Long? = null,
)
