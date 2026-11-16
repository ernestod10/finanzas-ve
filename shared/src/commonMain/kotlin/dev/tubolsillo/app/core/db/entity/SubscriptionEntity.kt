package dev.tubolsillo.app.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Suscripción cargada a mano (Netflix, gym, hosting…): nombre, precio y el
 * día del mes en que se cobra. Las detectadas por la app viven en el plan; esta
 * tabla es para las que el usuario quiere anotar él mismo.
 */
@Entity(
    tableName = "manual_subscriptions",
    indices = [Index("dayOfMonth")],
)
@Serializable
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountMinor: Long,
    val currency: String,
    /** Día del mes del cobro (1-31). */
    val dayOfMonth: Int,
    val isActive: Boolean = true,
    val createdAt: Long,
)
