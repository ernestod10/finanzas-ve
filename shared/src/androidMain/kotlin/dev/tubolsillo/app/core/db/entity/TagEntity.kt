package dev.tubolsillo.app.core.db.entity

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Etiqueta libre de gasto (ej. "Comida", "Transporte", "Salud").
 * Son las categorías del presupuesto mensual: el usuario etiqueta cada
 * transacción y el presupuesto agrupa por etiqueta.
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
@Serializable
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)
