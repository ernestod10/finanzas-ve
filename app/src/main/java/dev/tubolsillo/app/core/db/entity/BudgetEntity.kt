package dev.tubolsillo.app.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Presupuesto mensual por etiqueta (categoría de gasto).
 * month = "yyyy-MM" (ej. "2026-09"); tagName = etiqueta o "TOTAL".
 */
@Entity(
    tableName = "budgets",
    indices = [Index(value = ["month", "tagName"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: String,
    val tagName: String,
    val limitMinor: Long,
    val currency: String,
)
