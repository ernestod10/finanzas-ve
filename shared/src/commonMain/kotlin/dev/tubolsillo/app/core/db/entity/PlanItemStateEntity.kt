package dev.tubolsillo.app.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Estado de una línea del plan EN UN MES concreto: si ya se pagó/cobró y con
 * qué transacción real quedó enlazada.
 *
 * Existe separado de [PlanItemEntity] porque el estado es por ocurrencia: el
 * mismo seguro recurrente está pagado en agosto y pendiente en septiembre. La
 * clave lógica es (itemId, month); para una línea puntual solo hay una fila.
 *
 * [amountMinor] permite corregir el estimado de un mes sin tocar la regla (el
 * recibo llegó más caro), y queda como el monto esperado de ese mes.
 */
@Entity(
    tableName = "plan_item_states",
    indices = [Index(value = ["itemId", "month"], unique = true)],
)
@Serializable
data class PlanItemStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    /** Mes de la ocurrencia, "yyyy-MM". */
    val month: String,
    /** "PENDING" | "PAID" | "SKIPPED". */
    val status: String,
    /** Transacción real enlazada (conciliación). */
    val txId: Long? = null,
    /** Monto esperado de ese mes, si difiere del estimado de la regla. */
    val amountMinor: Long? = null,
    val paidAt: Long? = null,
)
