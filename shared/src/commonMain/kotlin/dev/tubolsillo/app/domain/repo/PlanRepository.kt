package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.db.dao.PlanItemDao
import dev.tubolsillo.app.core.db.dao.PlanItemStateDao
import dev.tubolsillo.app.core.db.dao.TransactionDao
import dev.tubolsillo.app.core.db.entity.PlanItemEntity
import dev.tubolsillo.app.core.db.entity.PlanItemStateEntity
import dev.tubolsillo.app.core.time.MonthBuckets
import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.domain.model.PlanKind
import dev.tubolsillo.app.domain.model.PlanMonthData
import dev.tubolsillo.app.domain.model.PlanRecurrence
import dev.tubolsillo.app.domain.model.PlanStatus
import dev.tubolsillo.app.domain.usecase.PlanCalculator
import dev.tubolsillo.app.domain.usecase.RateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** Resultado de conciliar una línea con un movimiento real. */
sealed interface LinkResult {
    data object Ok : LinkResult

    /** Ese movimiento ya es el real de otra línea del plan. */
    data object AlreadyLinked : LinkResult
}

/**
 * Plan del mes: ingresos y gastos estimados contra lo real.
 *
 * Es reactivo como el presupuesto por categoría: cualquier cambio en las
 * líneas, en el estado del mes, en las transacciones o en las tasas recalcula
 * la vista. La conciliación (enlazar una línea con su transacción) se guarda en
 * plan_item_states y es siempre por mes.
 */
class PlanRepository(
    private val planItemDao: PlanItemDao,
    private val planItemStateDao: PlanItemStateDao,
    private val transactionDao: TransactionDao,
    private val rateProvider: RateProvider,
    private val planCalculator: PlanCalculator,
) {

    fun observeMonth(month: String): Flow<PlanMonthData> {
        val (start, end) = MonthBuckets.bounds(month)
        return combine(
            planItemDao.observeActive(),
            planItemStateDao.observeForMonth(month),
            transactionDao.observeBetween(start, end),
            rateProvider.observeRates(),
        ) { items, states, transactions, rates ->
            planCalculator.monthData(month, items, states, transactions, rates)
        }.flowOn(Dispatchers.Default)
    }

    suspend fun addItem(
        kind: PlanKind,
        label: String,
        amountMinor: Long,
        currencyCode: String,
        recurrence: PlanRecurrence,
        dueAt: Long?,
        dayOfMonth: Int?,
        categoryId: Long?,
        month: String,
    ): Long = planItemDao.insert(
        PlanItemEntity(
            kind = kind.key,
            label = label,
            amountMinor = amountMinor,
            currency = currencyCode,
            recurrence = recurrence.key,
            // Un recurrente empieza a aplicar el mes en que se crea, salvo que
            // el usuario elija otra fecha; una línea puntual lleva su fecha.
            dueAt = if (recurrence == PlanRecurrence.ONCE) dueAt else null,
            dayOfMonth = if (recurrence == PlanRecurrence.MONTHLY) dayOfMonth else null,
            startMonth = if (recurrence == PlanRecurrence.MONTHLY) month else null,
            categoryId = categoryId,
        )
    )

    suspend fun updateItem(item: PlanItemEntity) {
        planItemDao.update(item.copy(updatedAt = nowMillis()))
    }

    suspend fun itemById(itemId: Long): PlanItemEntity? = planItemDao.getById(itemId)

    /**
     * Saca la línea del plan sin borrar su historia: el estado mensual y los
     * pagos ya conciliados se conservan, y la fila se puede reactivar.
     */
    suspend fun deleteItem(itemId: Long) {
        planItemDao.getById(itemId)?.let {
            planItemDao.update(it.copy(isActive = false, updatedAt = nowMillis()))
        }
    }

    /** Marca pendiente/pagado a mano, sin enlazar transacción. */
    suspend fun setStatus(itemId: Long, month: String, status: PlanStatus) {
        val existing = planItemStateDao.getState(itemId, month)
        planItemStateDao.upsert(
            existing?.copy(status = status.key, txId = null, paidAt = null)
                ?: PlanItemStateEntity(itemId = itemId, month = month, status = status.key)
        )
    }

    /**
     * Marca la línea como pagada y la enlaza a la transacción real.
     * Un movimiento no puede quedar enlazado a dos líneas: el primer enlace gana.
     */
    suspend fun linkTransaction(itemId: Long, month: String, txId: Long): LinkResult {
        val other = planItemStateDao.findByTx(txId)
        if (other != null && other.itemId != itemId) return LinkResult.AlreadyLinked
        val now = nowMillis()
        val existing = planItemStateDao.getState(itemId, month)
        planItemStateDao.upsert(
            existing?.copy(status = PlanStatus.PAID.key, txId = txId, paidAt = now)
                ?: PlanItemStateEntity(
                    itemId = itemId,
                    month = month,
                    status = PlanStatus.PAID.key,
                    txId = txId,
                    paidAt = now,
                )
        )
        return LinkResult.Ok
    }
}
