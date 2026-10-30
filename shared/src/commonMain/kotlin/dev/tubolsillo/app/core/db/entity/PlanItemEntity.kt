package dev.tubolsillo.app.core.db.entity

import dev.tubolsillo.app.core.time.nowMillis

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Línea del plan del mes: un ingreso o gasto ESTIMADO.
 *
 * Es la regla, no la ocurrencia: una línea recurrente (seguro, sueldo) se
 * escribe UNA vez y aplica a todos los meses dentro de [startMonth]..[endMonth];
 * una puntual (matrícula) lleva [dueAt] y vive en un solo mes. Las ocurrencias
 * NO se materializan en la base: se derivan al leer (ver PlanCalculator), así
 * que editar el monto de un recurrente corrige todos los meses y no hace falta
 * ningún job de sincronización.
 *
 * El estado por mes (pagado/pendiente y la transacción real enlazada) vive
 * aparte, en plan_item_states, para no retroactividad: marcar pagado agosto no
 * toca septiembre.
 */
@Entity(
    tableName = "plan_items",
    indices = [Index("categoryId")],
)
@Serializable
data class PlanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "INCOME" | "EXPENSE". */
    val kind: String,
    val label: String,
    /** Monto estimado en [currency], en unidades menores (centavos). */
    val amountMinor: Long,
    val currency: String,
    /** "ONCE" | "MONTHLY". */
    val recurrence: String,
    /** Fecha puntual (epoch ms). Solo para ONCE. */
    val dueAt: Long? = null,
    /** Día del mes 1..31. Solo para MONTHLY; se recorta al último día si el mes es más corto. */
    val dayOfMonth: Int? = null,
    /** Primer mes "yyyy-MM" en que aplica un recurrente (null = sin límite hacia atrás). */
    val startMonth: String? = null,
    /** Último mes "yyyy-MM" en que aplica un recurrente (null = sin fin). */
    val endMonth: String? = null,
    /** Categoría de gasto opcional (id de categories), para leer el estimado por categoría. */
    val categoryId: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = nowMillis(),
    val updatedAt: Long = nowMillis(),
)
