package dev.tubolsillo.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.domain.model.BudgetMonthData
import dev.tubolsillo.app.domain.model.PlanMonthData
import dev.tubolsillo.app.domain.repo.StatsData
import dev.tubolsillo.app.ui.budget.BudgetViewModel
import dev.tubolsillo.app.ui.components.AccountTileGrid
import dev.tubolsillo.app.ui.components.CardAction
import dev.tubolsillo.app.ui.components.CardHeader
import dev.tubolsillo.app.ui.components.MetricCard
import dev.tubolsillo.app.ui.components.PeriodSelector
import dev.tubolsillo.app.ui.components.SectionHeader
import dev.tubolsillo.app.ui.components.SegmentedTabs
import dev.tubolsillo.app.ui.components.ThinBar
import dev.tubolsillo.app.ui.components.TubolsilloCard
import dev.tubolsillo.app.ui.components.dayMonthLabel
import dev.tubolsillo.app.ui.components.greetingForNow
import dev.tubolsillo.app.ui.components.monthLabel
import dev.tubolsillo.app.ui.components.parseAccountColor
import dev.tubolsillo.app.ui.dashboard.AddAccountDialog
import dev.tubolsillo.app.ui.dashboard.DashboardViewModel
import dev.tubolsillo.app.ui.theme.TNUM
import dev.tubolsillo.app.ui.theme.TubolsilloColors
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Inicio del rediseño, con la composición de la referencia: dos pestañas
 * superiores (Cuentas / Presupuestos), saludo, tasas del día, saldo, alta
 * rápida, grilla de cuentas y métricas del período.
 */
@Composable
fun HomeScreen(
    onAccountClick: (Long) -> Unit,
    onNewRecord: (Boolean) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenRates: () -> Unit,
    onManageCategories: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
    budgetViewModel: BudgetViewModel = koinViewModel(),
) {
    val dashboard by viewModel.dashboard.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val plan by viewModel.planSummary.collectAsState()
    val rates by viewModel.rates.collectAsState()
    val ratesSnapshot by viewModel.ratesSnapshot.collectAsState()
    val month by viewModel.month.collectAsState()
    val budget by budgetViewModel.monthData.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAddAccount by remember { mutableStateOf(false) }

    val vesPerDollar = rates["VES"]?.let { if (it > 0) 1.0 / it else 1.0 } ?: 1.0
    val accounts = dashboard?.accountViews ?: emptyList()

    // Lista perezosa: cada tarjeta es un ítem, así solo se compone lo visible.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Column {
                Text(
                    text = "Inicio",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "TuBolsillo · Finanzas personales",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "tabs") {
            SegmentedTabs(
                items = listOf("Cuentas", "Presupuestos"),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
        }

        if (tab == 0) {
            item(key = "greeting") { GreetingCard() }
            item(key = "rates") {
                RatesCard(
                    snapshot = ratesSnapshot,
                    vesPerDollar = vesPerDollar,
                    onOpenRates = onOpenRates,
                )
            }
            item(key = "balance") {
                BalanceCard(
                    assetsUsd = dashboard?.totals?.assetsUsd ?: 0.0,
                    vesPerDollar = vesPerDollar,
                )
            }
            item(key = "new-record") { NewRecordCard(onNewRecord = onNewRecord) }
            item(key = "accounts-h") { SectionHeader(title = "Mis cuentas") }
            item(key = "accounts") {
                AccountTileGrid(
                    views = accounts,
                    onAccountClick = onAccountClick,
                    onAddAccount = { showAddAccount = true },
                )
            }
            item(key = "period") {
                PeriodSelector(
                    month = month,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                )
            }
            item(key = "metrics") { MetricsGrid(stats = stats) }
        } else {
            item(key = "period-b") {
                PeriodSelector(
                    month = month,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                )
            }
            item(key = "plan") { PlanCard(plan = plan, onOpenPlan = onOpenPlan) }
            item(key = "budget-h") {
                SectionHeader(
                    title = "Límites por categoría",
                    actionText = "Administrar",
                    onAction = onManageCategories,
                )
            }
            item(key = "budget") { BudgetCard(budget = budget) }
        }
    }

    if (showAddAccount) {
        AddAccountDialog(
            onDismiss = { showAddAccount = false },
            onSave = { institution, name, kind, currency, initialMinor, limitMinor, colorHex ->
                viewModel.addAccount(
                    institution = institution,
                    name = name,
                    kind = kind,
                    currencyCode = currency,
                    initialBalanceMinor = initialMinor,
                    creditLimitMinor = limitMinor,
                    colorHex = colorHex,
                )
                showAddAccount = false
            },
        )
    }
}

/** Tarjeta de saludo con degradado del acento y fecha a la derecha. */
@Composable
private fun GreetingCard() {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val primary = MaterialTheme.colorScheme.primary
    val start = if (dark) MaterialTheme.colorScheme.primaryContainer else primary
    val end = if (dark) {
        lerp(MaterialTheme.colorScheme.primaryContainer, primary, 0.30f)
    } else {
        lerp(primary, Color.White, 0.22f)
    }
    val ink = if (dark) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onPrimary

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(start, end))),
        ) {
            // Dos círculos apenas visibles: dan profundidad sin textura.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 44.dp, y = (-36).dp)
                    .size(120.dp)
                    .background(ink.copy(alpha = 0.08f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 70.dp, y = 46.dp)
                    .size(96.dp)
                    .background(ink.copy(alpha = 0.06f), CircleShape),
            )

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = greetingForNow(),
                        style = MaterialTheme.typography.labelSmall,
                        color = ink.copy(alpha = 0.85f),
                    )
                    Text(
                        text = dayMonthLabel(nowMillis()),
                        style = MaterialTheme.typography.labelSmall,
                        color = ink.copy(alpha = 0.85f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tu dinero, claro y en orden.",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ink,
                )
            }
        }
    }
}

@Composable
private fun RatesCard(
    snapshot: dev.tubolsillo.app.domain.repo.RatesSnapshot?,
    vesPerDollar: Double,
    onOpenRates: () -> Unit,
) {
    fun inBs(value: Double): String =
        MoneyFormatter.formatMinor((value * 100).toLong(), "VES")

    // Con snapshot se muestran las referencias REALES del endpoint (BCV y P2P),
    // no la tasa convertida que usa el patrimonio. Sin snapshot, la tasa
    // guardada se rotula sin apellido para no mentir sobre su fuente.
    val dollarLabel = if (snapshot != null) "Dólar BCV" else "Dólar"
    val dollarValue = if (snapshot != null) inBs(snapshot.bcvUsdBs)
    else if (vesPerDollar > 0) inBs(vesPerDollar) else "—"

    val euroLabel = if (snapshot != null) "Euro BCV" else "Euro"
    val euroValue = snapshot?.bcvEurBs?.takeIf { it > 0 }?.let { inBs(it) } ?: "—"

    val usdtLabel = "USDT P2P"
    val usdtValue = snapshot?.p2pUsdtVes?.takeIf { it > 0 }?.let { inBs(it) } ?: "—"

    TubolsilloCard {
        CardHeader(
            title = "Tasas de hoy",
            icon = Icons.Filled.CurrencyExchange,
            action = { CardAction("Calculadora", onOpenRates) },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RateTile(dollarLabel, dollarValue, Modifier.weight(1f))
            RateTile(euroLabel, euroValue, Modifier.weight(1f))
            RateTile(usdtLabel, usdtValue, Modifier.weight(1f))
        }
        if (snapshot == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Actualizando referencias…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RateTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = TubolsilloColors.tileFill,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TNUM,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** El total en cuentas, con su equivalencia en bolívares. */
@Composable
private fun BalanceCard(assetsUsd: Double, vesPerDollar: Double) {
    TubolsilloCard {
        Text(
            text = "Tu saldo en cuentas",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = MoneyFormatter.formatUsd(assetsUsd),
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = TNUM,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "≈ " + MoneyFormatter.formatMinor((assetsUsd * vesPerDollar * 100).toLong(), "VES"),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TNUM),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Alta rápida: dos acciones sobre el acento, como en la referencia. */
@Composable
private fun NewRecordCard(onNewRecord: (Boolean) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onPrimary
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = primary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Nuevo registro",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ink,
            )
            Spacer(Modifier.height(12.dp))
            QuickAction("Ingreso", Icons.Filled.ArrowDownward, ink) { onNewRecord(true) }
            Spacer(Modifier.height(8.dp))
            QuickAction("Gasto", Icons.Filled.ArrowUpward, ink) { onNewRecord(false) }
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    ink: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ink.copy(alpha = 0.14f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = ink,
            )
        }
    }
}

@Composable
private fun MetricsGrid(stats: StatsData?) {
    val metrics = stats?.metrics
    val previous = stats?.previousMetrics

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard(
            label = "Ingreso",
            value = MoneyFormatter.formatUsd(metrics?.incomeUsd ?: 0.0),
            icon = Icons.Filled.TrendingUp,
            delta = changeLabel(metrics?.incomeUsd, previous?.incomeUsd, higherIsBetter = true).first,
            deltaPositive = changeLabel(metrics?.incomeUsd, previous?.incomeUsd, true).second,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = "Gasto",
            value = MoneyFormatter.formatUsd(metrics?.expenseUsd ?: 0.0),
            icon = Icons.Filled.Payments,
            delta = changeLabel(metrics?.expenseUsd, previous?.expenseUsd, higherIsBetter = false).first,
            deltaPositive = changeLabel(metrics?.expenseUsd, previous?.expenseUsd, false).second,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard(
            label = "Flujo neto",
            value = MoneyFormatter.formatUsd(metrics?.netUsd ?: 0.0),
            icon = Icons.Filled.Savings,
            delta = changeLabel(metrics?.netUsd, previous?.netUsd, higherIsBetter = true).first,
            deltaPositive = changeLabel(metrics?.netUsd, previous?.netUsd, true).second,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = "Movimientos",
            value = (metrics?.txCount ?: 0).toString(),
            icon = Icons.Filled.ReceiptLong,
            delta = if (previous?.txCount == 0) "— vs anterior"
            else signedDelta(metrics?.txCount?.toDouble(), previous?.txCount?.toDouble()) + " vs anterior",
            deltaPositive = null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlanCard(plan: PlanMonthData?, onOpenPlan: () -> Unit) {
    TubolsilloCard(onClick = onOpenPlan) {
        CardHeader(
            title = "Plan del mes",
            icon = Icons.Filled.EventNote,
            action = { CardAction("Ver", onOpenPlan) },
        )
        Spacer(Modifier.height(12.dp))
        if (plan == null || plan.isEmpty) {
            Text(
                text = "Sin plan cargado",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Carga tus ingresos y pagos con fecha para ver qué te falta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@TubolsilloCard
        }

        val hasExpenses = plan.expenses.isNotEmpty()
        val pending = if (hasExpenses) plan.pendingExpenseUsd else plan.pendingIncomeUsd
        val lines = if (hasExpenses) plan.expenses else plan.income
        val done = lines.count { it.isPaid }
        val label = if (hasExpenses) "Por pagar" else "Por cobrar"
        val progress = if (lines.isEmpty()) 0f else done.toFloat() / lines.size

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$done de ${lines.size} listos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = MoneyFormatter.formatUsd(pending),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TNUM,
                ),
                color = if (plan.overdueCount > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(10.dp))
        ThinBar(progress = progress, over = hasExpenses && plan.expenseOver)
        if (plan.overdueCount > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${plan.overdueCount} vencido(s) este mes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun BudgetCard(budget: BudgetMonthData?) {
    TubolsilloCard {
        if (budget == null || budget.rows.none { it.limitUsd != null || it.spentUsd > 0 }) {
            Text(
                text = "Todavía no hay límites por categoría.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Toca Administrar para crear tus categorías de gasto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@TubolsilloCard
        }

        val rows = budget.rows.filter { it.limitUsd != null || it.spentUsd > 0.0 }
        rows.forEachIndexed { index, row ->
            val color = parseAccountColor(row.colorHex) ?: MaterialTheme.colorScheme.primary
            val limit = row.limitUsd
            val progress = if (limit != null && limit > 0) (row.spentUsd / limit).toFloat() else 1f
            val over = limit != null && row.spentUsd > limit

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.tagName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (limit != null) {
                        MoneyFormatter.formatUsd(row.spentUsd) + " de " + MoneyFormatter.formatUsd(limit)
                    } else {
                        MoneyFormatter.formatUsd(row.spentUsd)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TNUM),
                    color = if (over) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            CategoryBar(
                progress = progress,
                color = if (over) MaterialTheme.colorScheme.error else color,
            )
            if (index != rows.lastIndex) Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun CategoryBar(progress: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f), RoundedCornerShape(50)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(5.dp)
                .background(color, RoundedCornerShape(50)),
        )
    }
}

/** "▲ 12 % vs anterior" con el color según si la dirección es buena. */
private fun changeLabel(
    current: Double?,
    previous: Double?,
    higherIsBetter: Boolean,
): Pair<String, Boolean?> {
    if (current == null || previous == null || previous == 0.0) {
        return "— vs período anterior" to null
    }
    val pct = (current - previous) / abs(previous) * 100.0
    val text = (if (pct >= 0) "+" else "−") + abs(pct).roundToInt() + " % vs anterior"
    return text to if (higherIsBetter) pct >= 0 else pct <= 0
}

private fun signedDelta(current: Double?, previous: Double?): String {
    if (current == null || previous == null) return "—"
    val diff = (current - previous).roundToInt()
    return (if (diff >= 0) "+" else "−") + abs(diff)
}
