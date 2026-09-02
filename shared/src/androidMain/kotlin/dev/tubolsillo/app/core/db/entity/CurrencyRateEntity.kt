package dev.tubolsillo.app.core.db.entity

import kotlinx.serialization.Serializable

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Tasa de conversión: 1 unidad de [currency] = [usdRate] USD. */
@Entity(tableName = "currency_rates")
@Serializable
data class CurrencyRateEntity(
    @PrimaryKey val currency: String,
    val usdRate: Double,
    val source: String,
    val updatedAt: Long,
)
