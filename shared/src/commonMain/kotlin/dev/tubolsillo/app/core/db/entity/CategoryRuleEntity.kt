package dev.tubolsillo.app.core.db.entity

import dev.tubolsillo.app.core.time.nowMillis

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Regla de auto-etiquetado: si el comercio normalizado contiene [merchantPattern],
 * la transacción se etiqueta con [tagName] (y opcionalmente se le asigna [categoryKey]).
 * Se crean desde las correcciones del usuario ("FARMACIA X" → Salud) y se aplican
 * en cada ingesta (SMS, push, manual, CSV).
 */
@Entity(
    tableName = "category_rules",
    indices = [Index(value = ["merchantPattern"], unique = true)],
)
@Serializable
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantPattern: String,
    val tagName: String? = null,
    val categoryKey: String? = null,
    val createdAt: Long = nowMillis(),
)
