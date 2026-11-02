package dev.tubolsillo.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.ai.AnalyzerAvailability
import dev.tubolsillo.app.domain.repo.StatsData
import dev.tubolsillo.app.domain.usecase.CategorySpend
import dev.tubolsillo.app.domain.usecase.TrendPoint
import dev.tubolsillo.app.ui.components.CardHeader
import dev.tubolsillo.app.ui.components.PeriodSelector
import dev.tubolsillo.app.ui.components.TubolsilloCard
import dev.tubolsillo.app.ui.components.charts.DonutChart
import dev.tubolsillo.app.ui.components.charts.DonutSlice
import dev.tubolsillo.app.ui.components.charts.TrendChart
import dev.tubolsillo.app.ui.components.dayMonthLabel
import dev.tubolsillo.app.ui.components.parseAccountColor
import dev.tubolsillo.app.ui.dashboard.DashboardViewModel
import dev.tubolsillo.app.ui.theme.TNUM
import dev.tubolsillo.app.ui.theme.TubolsilloColors
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

/** Paleta categórica para la dona cuando la categoría no tiene color propio. */
private val CHART_PALETTE = listOf(
    Color(0xFFED494A),
    Color(0xFF3D81F6),
    Color(0xFF28C45F),
    Color(0xFF7B3DEC),
    Color(0xFFF59F19),
    Color(0xFF16A599),
    Color(0xFF9A9DA1),
    Color(0xFFC2410C),
)

/**
 * Estadísticas: cómo se movió el saldo y en qué se fue el gasto del período.
 *
 * Son dos vistas del mismo período que Inicio: el mes es compartido, así que
 * cambiar de mes acá también lo cambia en Inicio.
 */
@Composable
fun StatsScreen(viewModel: DashboardViewModel = koinViewModel()) {
    val stats by viewModel.stats.collectAsState()
    val month by viewModel.month.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val summaryBusy by viewModel.summaryBusy.collectAsState()
    val analyzerAvailability by viewModel.analyzerAvailability.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Estadísticas",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Cómo se movió tu dinero",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        PeriodSelector(
            month = month,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
        )
        Spacer(Modifier.height(12.dp))
        MonthSummaryCard(
            summary = summary,
            busy = summaryBusy,
            availability = analyzerAvailability,
            monthKey = month,
            onGenerate = viewModel::generateSummary,
        )
        Spacer(Modifier.height(12.dp))
        TrendCard(stats)
        Spacer(Modifier.height(12.dp))
        DonutCard(stats)
    }
}

@Composable
private fun MonthSummaryCard(
    summary: String?,
    busy: Boolean,
    availability: AnalyzerAvailability,
    monthKey: String,
    onGenerate: () -> Unit,
) {
    TubolsilloCard {
        CardHeader(title = "Resumen del mes", icon = Icons.Filled.AutoAwesome)
        Spacer(Modifier.height(8.dp))
        when {
            busy -> {
                Text(
                    text = "Redactando con el modelo on-device…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                )
            }

            summary != null -> Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            availability != AnalyzerAvailability.AVAILABLE -> Text(
                text = "Requiere Gemini Nano disponible en este dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                Text(
                    text = "Un párrafo con lo que pasó en " +
                    dev.tubolsillo.app.ui.components.monthLabel(monthKey) + ", a partir de tus números.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onGenerate) { Text("Generar resumen") }
            }
        }
    }
}

@Composable
private fun TrendCard(stats: StatsData?) {
    TubolsilloCard {
        CardHeader(title = "Tendencia del saldo", icon = Icons.Filled.ShowChart)
        val trend = stats?.trend
        if (trend == null || !trend.hasEnoughPoints) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Todavía no hay historial suficiente",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "La serie del mes se arma con cada saldo que capturamos. " +
                    "Con dos días distintos ya se dibuja.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@TubolsilloCard
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SALDO ACTUAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = MoneyFormatter.formatUsd(trend.lastUsd),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = TNUM,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            DeltaChip(
                pct = trend.changePct,
                caption = "vs. inicio del mes",
                higherIsBetter = true,
            )
        }
        Spacer(Modifier.height(14.dp))
        TrendChart(
            values = trend.points.map { it.valueUsd },
            xLabels = labelsFor(trend.points),
            lineColor = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Serie del mes según tus capturas de saldo. La conversión usa la tasa de hoy, no la histórica.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DonutCard(stats: StatsData?) {
    TubolsilloCard {
        CardHeader(title = "Estructura de gastos", icon = Icons.Filled.PieChart)
        val spends = stats?.expenseByCategory.orEmpty()
        val total = spends.sumOf { it.amountUsd }

        if (spends.isEmpty() || total <= 0.0) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Sin gastos en este período",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Cuando haya movimientos de salida vas a ver en qué se fue la plata.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@TubolsilloCard
        }

        val metrics = stats?.metrics
        val previous = stats?.previousMetrics
        val delta = if (metrics != null && previous != null) {
            changePct(metrics.expenseUsd, previous.expenseUsd)
        } else null

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TOTAL DEL PERÍODO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = MoneyFormatter.formatUsd(total),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = TNUM,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            DeltaChip(
                pct = delta,
                caption = "vs. mes anterior",
                higherIsBetter = false,
                showZeroAsNeutral = true,
            )
        }

        Spacer(Modifier.height(8.dp))
        val slices = spends.mapIndexed { index, spend ->
            DonutSlice(
                label = spend.name,
                value = spend.amountUsd,
                color = parseAccountColor(spend.colorHex) ?: CHART_PALETTE[index % CHART_PALETTE.size],
            )
        }
        val top = spends.first()
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(
                slices = slices,
                centerTitle = top.name,
                centerValue = MoneyFormatter.formatUsd(top.amountUsd),
                centerDelta = shareLabel(top, total),
                centerDeltaColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Legend(slices)
    }
}

@Composable
private fun DeltaChip(
    pct: Double?,
    caption: String,
    higherIsBetter: Boolean,
    showZeroAsNeutral: Boolean = false,
) {
    if (pct == null) {
        Text(
            text = "— $caption",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val rounded = pct.roundToInt()
    val neutral = showZeroAsNeutral && rounded == 0
    val good = if (higherIsBetter) pct >= 0 else pct <= 0
    val color = when {
        neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        good -> TubolsilloColors.positive
        else -> TubolsilloColors.negative
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = (if (pct >= 0) "+" else "−") + abs(rounded) + " %",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TNUM,
                ),
                color = color,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Legend(slices: List<DonutSlice>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slices.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { slice ->
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(slice.color, CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = slice.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun labelsFor(points: List<TrendPoint>): List<String> {
    if (points.size < 2) return emptyList()
    val indices = listOf(
        0,
        points.size / 4,
        points.size / 2,
        points.size * 3 / 4,
        points.lastIndex,
    ).distinct()
    return indices.map { dayMonthLabel(points[it].epochMs) }
}

private fun changePct(current: Double, previous: Double): Double? =
    if (previous == 0.0) null else (current - previous) / abs(previous) * 100.0

private fun shareLabel(spend: CategorySpend, total: Double): String =
    (spend.amountUsd / total * 100).roundToInt().toString() + " % del gasto"
