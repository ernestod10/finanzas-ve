package dev.tubolsillo.app.ui.period

import dev.tubolsillo.app.core.time.MonthBuckets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Mes compartido por Inicio y Estadísticas.
 *
 * Vive en Koin como singleton para que cambiar el período en una pantalla se
 * refleje en la otra: son dos vistas del mismo período, no dos estados.
 */
class PeriodState {
    private val _month = MutableStateFlow(MonthBuckets.monthKeyNow())
    val month: StateFlow<String> = _month

    fun shift(months: Int) {
        _month.value = MonthBuckets.plusMonths(_month.value, months)
    }

    fun goToCurrentMonth() {
        _month.value = MonthBuckets.monthKeyNow()
    }
}
