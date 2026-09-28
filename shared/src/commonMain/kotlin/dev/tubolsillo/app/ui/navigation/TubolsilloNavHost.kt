package dev.tubolsillo.app.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import dev.tubolsillo.app.ui.components.GlassSurface
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import dev.tubolsillo.app.ui.portal.PortalScreen
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
    const val PORTAL = "portal"

    fun account(accountId: Long) = "account/$accountId"
}


/**
 * Transición de empuje al estilo iOS: la pantalla nueva entra desde la derecha
 * y la anterior se corre un tercio a la izquierda, no se desvanece. Al volver,
 * el recorrido se invierte.
 *
 * Las pestañas NO la usan: en iOS cambiar de pestaña no desliza, así que ahí el
 * cambio es directo y sin desplazamiento.
 */
private const val PUSH_MS = 340

private fun iosPushTransitions(): NavTransitionSet = NavTransitionSet(
    enter = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(PUSH_MS, easing = FastOutSlowInEasing),
    ),
    exit = slideOutHorizontally(
        targetOffsetX = { -it / 3 },
        animationSpec = tween(PUSH_MS, easing = FastOutSlowInEasing),
    ),
    popEnter = slideInHorizontally(
        initialOffsetX = { -it / 3 },
        animationSpec = tween(PUSH_MS, easing = FastOutSlowInEasing),
    ),
    popExit = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(PUSH_MS, easing = FastOutSlowInEasing),
    ),
)

private data class NavTransitionSet(
    val enter: EnterTransition,
    val exit: ExitTransition,
    val popEnter: EnterTransition,
    val popExit: ExitTransition,
)

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
)

@Composable
fun TubolsilloNavHost() {
    val navController = rememberNavController()

    // Orden pedido por el usuario: cuentas · movimientos · inicio · presupuesto · ajustes.
    val destinations = listOf(
        TopLevelDestination(Routes.ACCOUNTS, "Cuentas", { tint -> Icon(Icons.Filled.AccountBalance, null, tint = tint) }),
        TopLevelDestination(Routes.TRANSACTIONS, "Movimientos", { tint -> Icon(Icons.Filled.ReceiptLong, null, tint = tint) }),
        TopLevelDestination(Routes.DASHBOARD, "Inicio", { tint -> Icon(Icons.Filled.Home, null, tint = tint) }),
        TopLevelDestination(Routes.BUDGET, "Presupuesto", { tint -> Icon(Icons.Filled.AccountBalanceWallet, null, tint = tint) }),
        TopLevelDestination(Routes.SETTINGS, "Ajustes", { tint -> Icon(Icons.Filled.Settings, null, tint = tint) }),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            // Barra flotante de la capa funcional: glass, sin sombra, y el
            // contenido pasa por debajo (las pantallas reservan el alto al
            // final de su lista). El acento se reserva para la pestaña activa.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                GlassSurface(shape = RoundedCornerShape(26.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        destinations.forEach { destination ->
                            val selected = currentDestination?.hierarchy
                                ?.any { it.route == destination.route } == true
                            val tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    .padding(vertical = 7.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                destination.icon(tint)
                                Text(
                                    text = destination.label,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = tint,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(innerPadding),
            // Cambiar de pestaña en iOS no anima: el contenido simplemente
            // aparece. El slide queda para las pantallas que se empujan.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
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
            composable(
                route = Routes.CATEGORIES,
                enterTransition = { iosPushTransitions().enter },
                exitTransition = { iosPushTransitions().exit },
                popEnterTransition = { iosPushTransitions().popEnter },
                popExitTransition = { iosPushTransitions().popExit },
            ) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onRatesClick = { navController.navigate(Routes.RATES) },
                    onBinanceClick = { navController.navigate(Routes.BINANCE) },
                    onEventsClick = { navController.navigate(Routes.EVENTS) },
                    onCsvImportClick = { navController.navigate(Routes.CSV_IMPORT) },
                    onPortalClick = { navController.navigate(Routes.PORTAL) },
                )
            }
            composable(
                route = Routes.PORTAL,
                enterTransition = { iosPushTransitions().enter },
                exitTransition = { iosPushTransitions().exit },
                popEnterTransition = { iosPushTransitions().popEnter },
                popExitTransition = { iosPushTransitions().popExit },
            ) {
                PortalScreen(onBack = { navController.popBackStack() })
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
            composable(
                route = Routes.RATES,
                enterTransition = { iosPushTransitions().enter },
                exitTransition = { iosPushTransitions().exit },
                popEnterTransition = { iosPushTransitions().popEnter },
                popExitTransition = { iosPushTransitions().popExit },
            ) {
                RatesScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.BINANCE,
                enterTransition = { iosPushTransitions().enter },
                exitTransition = { iosPushTransitions().exit },
                popEnterTransition = { iosPushTransitions().popEnter },
                popExitTransition = { iosPushTransitions().popExit },
            ) {
                BinanceSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.EVENTS,
                enterTransition = { iosPushTransitions().enter },
                exitTransition = { iosPushTransitions().exit },
                popEnterTransition = { iosPushTransitions().popEnter },
                popExitTransition = { iosPushTransitions().popExit },
            ) {
                EventsScreen(
                    onBack = { navController.popBackStack() },
                    onReplayClick = { navController.navigate(Routes.REPLAY) },
                )
            }
            composable(
                route = Routes.REPLAY,
                enterTransition = { iosPushTransitions().enter },
                exitTransition = { iosPushTransitions().exit },
                popEnterTransition = { iosPushTransitions().popEnter },
                popExitTransition = { iosPushTransitions().popExit },
            ) {
                ReplayScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.CSV_IMPORT,
                enterTransition = { iosPushTransitions().enter },
                exitTransition = { iosPushTransitions().exit },
                popEnterTransition = { iosPushTransitions().popEnter },
                popExitTransition = { iosPushTransitions().popExit },
            ) {
                CsvImportScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
