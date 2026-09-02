package dev.ernesto.finanza.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/** Rutas de la app. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val TRANSACTIONS = "transactions"
    const val SETTINGS = "settings"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
)

/**
 * Host de navegación con barra inferior (Dashboard / Movimientos / Ajustes).
 * Fase 0: pantallas placeholder; Fase 1 las reemplaza por las reales.
 */
@Composable
fun FinanzaNavHost() {
    val navController = rememberNavController()

    val destinations = listOf(
        TopLevelDestination(Routes.DASHBOARD, "Inicio", { Icon(Icons.Filled.Home, null) }),
        TopLevelDestination(Routes.TRANSACTIONS, "Movimientos", { Icon(Icons.Filled.ReceiptLong, null) }),
        TopLevelDestination(Routes.SETTINGS, "Ajustes", { Icon(Icons.Filled.Settings, null) }),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
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
                        label = { Text(destination.label) },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.DASHBOARD) { PlaceholderScreen("Dashboard") }
            composable(Routes.TRANSACTIONS) { PlaceholderScreen("Movimientos") }
            composable(Routes.SETTINGS) { PlaceholderScreen("Ajustes") }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title)
    }
}
