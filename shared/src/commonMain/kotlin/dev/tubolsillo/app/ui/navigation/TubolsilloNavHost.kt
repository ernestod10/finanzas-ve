package dev.tubolsillo.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.tubolsillo.app.ui.account.AccountDetailScreen
import dev.tubolsillo.app.ui.accounts.AccountsScreen
import dev.tubolsillo.app.ui.budget.BudgetScreen
import dev.tubolsillo.app.ui.categories.CategoriesScreen
import dev.tubolsillo.app.ui.dashboard.DashboardScreen
import dev.tubolsillo.app.ui.events.EventsScreen
import dev.tubolsillo.app.ui.events.ReplayScreen
import dev.tubolsillo.app.ui.importcsv.CsvImportScreen
import dev.tubolsillo.app.ui.settings.BinanceSettingsScreen
import dev.tubolsillo.app.ui.settings.RatesScreen
import dev.tubolsillo.app.ui.settings.SettingsScreen
import dev.tubolsillo.app.ui.transactions.TransactionsScreen

/** Rutas de la app. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val ACCOUNTS = "accounts"
    const val TRANSACTIONS = "transactions"
    const val BUDGET = "budget"
    const val SETTINGS = "settings"
    const val ACCOUNT = "account/{accountId}"
    const val RATES = "rates"
    const val BINANCE = "binance"
    const val EVENTS = "events"
    const val REPLAY = "replay"
    const val CSV_IMPORT = "csv_import"
    const val CATEGORIES = "categories"

    fun account(accountId: Long) = "account/$accountId"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
)

@Composable
fun TubolsilloNavHost() {
    val navController = rememberNavController()

    // Orden pedido por el usuario: cuentas · movimientos · inicio · presupuesto · ajustes.
    val destinations = listOf(
        TopLevelDestination(Routes.ACCOUNTS, "Cuentas", { Icon(Icons.Filled.AccountBalance, null) }),
        TopLevelDestination(Routes.TRANSACTIONS, "Movimientos", { Icon(Icons.Filled.ReceiptLong, null) }),
        TopLevelDestination(Routes.DASHBOARD, "Inicio", { Icon(Icons.Filled.Home, null) }),
        TopLevelDestination(Routes.BUDGET, "Presupuesto", { Icon(Icons.Filled.AccountBalanceWallet, null) }),
        TopLevelDestination(Routes.SETTINGS, "Ajustes", { Icon(Icons.Filled.Settings, null) }),
    )

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            // Barra flotante tipo "isla".
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    ),
                ) {
                    NavigationBar(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                selected = currentDestination?.hierarchy
                                    ?.any { it.route == destination.route } == true,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = destination.icon,
                                label = {
                                    Text(
                                        text = destination.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 10.sp,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onAccountClick = { id -> navController.navigate(Routes.account(id)) },
                    onSeeAllTransactions = {
                        navController.navigate(Routes.TRANSACTIONS)
                    },
                )
            }
            composable(Routes.ACCOUNTS) {
                AccountsScreen(
                    onAccountClick = { id -> navController.navigate(Routes.account(id)) },
                )
            }
            composable(Routes.TRANSACTIONS) {
                TransactionsScreen()
            }
            composable(Routes.BUDGET) {
                BudgetScreen(
                    onManageCategories = { navController.navigate(Routes.CATEGORIES) },
                )
            }
            composable(Routes.CATEGORIES) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onRatesClick = { navController.navigate(Routes.RATES) },
                    onBinanceClick = { navController.navigate(Routes.BINANCE) },
                    onEventsClick = { navController.navigate(Routes.EVENTS) },
                    onCsvImportClick = { navController.navigate(Routes.CSV_IMPORT) },
                )
            }
            composable(
                route = Routes.ACCOUNT,
                arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
            ) { entry ->
                val accountId = entry.arguments?.getLong("accountId") ?: 0L
                AccountDetailScreen(
                    accountId = accountId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.RATES) {
                RatesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.BINANCE) {
                BinanceSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.EVENTS) {
                EventsScreen(
                    onBack = { navController.popBackStack() },
                    onReplayClick = { navController.navigate(Routes.REPLAY) },
                )
            }
            composable(Routes.REPLAY) {
                ReplayScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CSV_IMPORT) {
                CsvImportScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
