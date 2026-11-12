package dev.tubolsillo.app.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Categoría de gasto para el presupuesto mensual (Comida, Gimnasio, …).
 * CRUD libre del usuario; las de fábrica se siembran en la migración v4.
 * El nombre es único y el presupuesto mensual se ancla por nombre.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)],
)
@Serializable
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String? = null,
    val sortOrder: Int = 0,
    /** Clave del icono que muestra la app ("food", "car"…); null = genérico. */
    val iconKey: String? = null,
)
