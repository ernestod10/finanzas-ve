package dev.tubolsillo.app.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.ui.components.AccountRow
import dev.tubolsillo.app.ui.components.GroupHeader
import dev.tubolsillo.app.ui.components.GroupHeaderAction
import dev.tubolsillo.app.ui.components.GroupedGroup
import dev.tubolsillo.app.ui.components.HeaderAction
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.components.TxRow
import dev.tubolsillo.app.ui.theme.TNUM
import org.koin.compose.viewmodel.koinViewModel

/**
 * Inicio: el total, las cuentas y lo reciente. Estructura de lista agrupada de
 * iOS — un grupo por bloque, separadores sangrados — con el verde reservado
 * para cifras y acciones.
 */
@Composable
fun DashboardScreen(
    onAccountClick: (Long) -> Unit,
    onSeeAllTransactions: () -> Unit,
    onOpenPlan: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val dashboard by viewModel.dashboard.collectAsState()
    val plan by viewModel.planSummary.collectAsState()
    val vesPerDollar by viewModel.vesPerDollar.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    var showManualEntry by remember { mutableStateOf(false) }
    var showAddAccount by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<dev.tubolsillo.app.core.db.dao.TransactionWithTags?>(null) }

    val catById = remember(categories) { categories.associateBy { it.id } }

    IOSScreen(
        title = "Inicio",
        actions = { HeaderAction("Agregar") { showAddAccount = true } },
    ) {
        val data = dashboard

        if (data == null || data.accounts.isEmpty()) {
            item(key = "empty", contentType = "empty") { EmptyState(onAdd = { showAddAccount = true }) }
            item(key = "plan", contentType = "plan_summary") {
                PlanSummaryCard(
                    plan = plan,
                    onOpen = onOpenPlan,
                    onTogglePaid = { viewModel.togglePlanLinePaid(it) },
                )
            }
        } else {
            item(key = "hero", contentType = "hero") {
                HeroBlock(
                    assetsUsd = data.totals.assetsUsd,
                    debtUsd = data.totals.debtUsd,
                    netWorthUsd = data.totals.netWorthUsd,
                    vesPerDollar = vesPerDollar,
                    onRegister = { showManualEntry = true },
                )
            }

            item(key = "plan", contentType = "plan_summary") {
                PlanSummaryCard(
                    plan = plan,
                    onOpen = onOpenPlan,
                    liquidAssetsUsd = data.totals.assetsUsd,
                    onTogglePaid = { viewModel.togglePlanLinePaid(it) },
                )
            }

            val assets = data.assetViews
            val debts = data.debtViews

            if (assets.isNotEmpty()) {
                item(key = "assets-h", contentType = "header") { GroupHeader("Cuentas") }
                item(key = "assets", contentType = "group") {
                    GroupedGroup {
                        assets.forEachIndexed { index, view ->
                            AccountRow(
                                view = view,
                                onClick = { onAccountClick(view.accountId) },
                                showSeparator = index != assets.lastIndex,
                            )
                        }
                    }
                }
            }

            if (debts.isNotEmpty()) {
                item(key = "debts-h", contentType = "header") { GroupHeader("Tarjetas y líneas") }
                item(key = "debts", contentType = "group") {
                    GroupedGroup {
                        debts.forEachIndexed { index, view ->
                            AccountRow(
                                view = view,
                                onClick = { onAccountClick(view.accountId) },
                                showSeparator = index != debts.lastIndex,
                            )
                        }
                    }
                }
            }

            if (data.recentTransactions.isNotEmpty()) {
                item(key = "recent-h", contentType = "header") {
                    GroupHeaderAction(
                        text = "Recientes",
                        actionText = "Ver todos",
                        onAction = onSeeAllTransactions,
                    )
                }
                val recent = data.recentTransactions.take(5)
                item(key = "recent", contentType = "group") {
                    GroupedGroup {
                        recent.forEachIndexed { index, tx ->
                            val cat = tx.transaction.categoryId?.let { catById[it] }
                            TxRow(
                                tx = tx,
                                accountName = data.accountNames[tx.transaction.accountId],
                                onClick = { editingTx = tx },
                                categoryName = cat?.name,
                                categoryColorHex = cat?.colorHex,
                                showSeparator = index != recent.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showManualEntry) {
        ManualEntryDialog(
            accounts = dashboard?.accounts ?: emptyList(),
            categories = categories,
            onDismiss = { showManualEntry = false },
            onSave = { accountId, amountMinor, currency, description, category, userCategoryId, casheaInstallmentMinor ->
                viewModel.addManualTransaction(
                    accountId = accountId,
                    amountMinor = amountMinor,
                    currencyCode = currency,
                    description = description,
                    category = category,
                    userCategoryId = userCategoryId,
                    casheaInstallmentMinor = casheaInstallmentMinor,
                )
                showManualEntry = false
            },
        )
    }
    if (showAddAccount) {
        AddAccountDialog(
            onDismiss = { showAddAccount = false },
            onSave = { institution, name, kind, currency, initialMinor, limitMinor, colorHex ->
                viewModel.addAccount(institution, name, kind, currency, initialMinor, limitMinor, colorHex)
                showAddAccount = false
            },
        )
    }
    editingTx?.let { tx ->
        dev.tubolsillo.app.ui.account.TagTransactionDialog(
            tx = tx,
            allTags = allTags,
            categories = categories,
            onDismiss = { editingTx = null },
            onTag = { tagName, learn ->
                viewModel.tagTransaction(tx.transaction.id, tagName, learn)
                editingTx = null
            },
            onCategory = { categoryId ->
                viewModel.setTransactionCategory(tx.transaction.id, categoryId)
                editingTx = null
            },
            onUpdateDetails = { newDesc, newMinor ->
                viewModel.updateTransactionDetails(
                    tx.transaction.id,
                    newDesc,
                    newMinor,
                    tx.transaction.currency,
                )
                editingTx = null
            },
            onDelete = {
                viewModel.deleteTransaction(tx.transaction.id)
                editingTx = null
            },
        )
    }
}

/**
 * El bloque del patrimonio. Sin tarjeta: en iOS una cifra grande vive sobre el
 * fondo agrupado y el peso lo lleva el número, no un contenedor.
 */
@Composable
private fun HeroBlock(
    assetsUsd: Double,
    debtUsd: Double,
    netWorthUsd: Double,
    vesPerDollar: Double,
    onRegister: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 6.dp, bottom = 4.dp),
    ) {
        Text(
            text = "TOTAL EN CUENTAS",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        // El número es la interfaz: se le aprieta el tracking ópticamente porque
        // a 34sp las cifras tabulares quedan sueltas, y el aire lo aporta el
        // espacio, no el interletraje.
        Text(
            text = MoneyFormatter.formatUsd(assetsUsd),
            style = MaterialTheme.typography.displayMedium.copy(
                fontFeatureSettings = TNUM,
                letterSpacing = (-0.8).sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "≈ " + MoneyFormatter.formatMinor((assetsUsd * vesPerDollar * 100).toLong(), "VES"),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TNUM),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // Deudas y neto son datos, no alarmas: van en tinta secundaria. El rojo
        // se reserva para lo que exige atención, no para una cifra quieta.
        Text(
            text = "Deudas " + MoneyFormatter.formatUsd(debtUsd) +
                "  ·  Neto " + MoneyFormatter.formatUsd(netWorthUsd),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TNUM),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        androidx.compose.material3.FilledTonalButton(
            onClick = onRegister,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 18.dp,
                vertical = 8.dp,
            ),
        ) {
            Text(
                text = "Registrar movimiento",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Tu dinero en un solo lugar",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Agrega tu primer banco, tarjeta o wallet para ver tu total, tus deudas y tu presupuesto.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        HeaderAction("Agregar cuenta", onClick = onAdd)
    }
}
