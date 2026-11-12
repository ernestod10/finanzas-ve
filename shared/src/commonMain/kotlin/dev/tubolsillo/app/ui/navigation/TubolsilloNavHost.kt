package dev.tubolsillo.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.tubolsillo.app.ui.account.AccountDetailScreen
import dev.tubolsillo.app.ui.categories.CategoriesScreen
import dev.tubolsillo.app.ui.components.AppBottomBar
import dev.tubolsillo.app.ui.components.BottomBarItem
import dev.tubolsillo.app.ui.dashboard.DashboardViewModel
import dev.tubolsillo.app.ui.events.EventsScreen
import dev.tubolsillo.app.ui.events.ReplayScreen
import dev.tubolsillo.app.ui.home.HomeScreen
import dev.tubolsillo.app.ui.plan.PlanScreen
import dev.tubolsillo.app.ui.portal.PortalScreen
import dev.tubolsillo.app.ui.record.NewRecordScreen
import dev.tubolsillo.app.ui.review.EntryDraftHolder
import dev.tubolsillo.app.ui.review.ReviewScreen
import dev.tubolsillo.app.ui.classifier.ClassifierScreen
import dev.tubolsillo.app.ui.credits.CreditsScreen
import dev.tubolsillo.app.ui.settings.AiModelScreen
import dev.tubolsillo.app.ui.settings.BinanceSettingsScreen
import dev.tubolsillo.app.ui.settings.RatesScreen
import dev.tubolsillo.app.ui.settings.SettingsScreen
import dev.tubolsillo.app.ui.stats.StatsScreen
import dev.tubolsillo.app.ui.transactions.TransactionsScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Rutas de la app.
 *
 * Los cuatro destinos NO son rutas de navegación: son páginas de un
 * [HorizontalPager] que vive en [Routes.TABS]. Sus constantes quedan como
 * identidad de cada destino. Lo que se empuja encima —detalle de cuenta, plan,
 * tasas, nuevo registro— sí son rutas.
 */
object Routes {
    /** Anfitrión de las pestañas: la única ruta del pager. */
    const val TABS = "tabs"

    // Identidad de los destinos del pager, no destinos de navegación.
    const val HOME = "home"
    const val RECORDS = "records"
    const val STATS = "stats"
    const val MENU = "menu"

    // Pantallas empujadas sobre el pager.
    const val ACCOUNT = "account/{accountId}"
    const val RATES = "rates"
    const val BINANCE = "binance"
    const val EVENTS = "events"
    const val REPLAY = "replay"
    const val CATEGORIES = "categories"
    const val PORTAL = "portal"
    const val AI_MODEL = "ai_model"
    const val CREDITS = "credits"
    const val CLASSIFIER = "classifier"
    const val PLAN = "plan"
    const val RECORD = "record/{income}"
    const val REVIEW = "review"

    fun account(accountId: Long) = "account/$accountId"

    /** Alta manual: [income] preselecciona Ingreso en vez de Gasto. */
    fun record(income: Boolean) = "record/" + if (income) 1 else 0
}

/** Empuje al estilo iOS: 260ms ágil a 120Hz, la anterior se corre un tercio sin recrear objetos. */
private const val PUSH_MS = 260

private val PushEnterTransition: EnterTransition = slideInHorizontally(
    initialOffsetX = { it },
    animationSpec = tween(PUSH_MS, easing = FastOutSlowInEasing),
)

private val PushExitTransition: ExitTransition = slideOutHorizontally(
    targetOffsetX = { -it / 3 },
    animationSpec = tween(PUSH_MS, easing = FastOutSlowInEasing),
)

private val PopEnterTransition: EnterTransition = slideInHorizontally(
    initialOffsetX = { -it / 3 },
    animationSpec = tween(PUSH_MS, easing = FastOutSlowInEasing),
)

private val PopExitTransition: ExitTransition = slideOutHorizontally(
    targetOffsetX = { it },
    animationSpec = tween(PUSH_MS, easing = FastOutSlowInEasing),
)

/**
 * Los cuatro destinos en su orden visual. El FAB del centro no es un destino:
 * se dibuja superpuesto en la barra.
 */
private val TabDestinations = listOf(
    TopLevelDestination(Routes.HOME, "Inicio", Icons.Filled.Home),
    TopLevelDestination(Routes.RECORDS, "Registros", Icons.Filled.ReceiptLong),
    TopLevelDestination(Routes.STATS, "Estadísticas", Icons.Filled.BarChart),
    TopLevelDestination(Routes.MENU, "Menú", Icons.Filled.Menu),
)

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
fun TubolsilloNavHost() {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    // El estado del pager se iza aquí para que la barra y el contenido miren lo
    // mismo: la barra lo lee para resaltar y él lo mueve al tocar un destino.
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { TabDestinations.size },
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val onTabs = backStackEntry?.destination?.route == Routes.TABS

    /**
     * Cambiar de destino. Si hay una pantalla empujada encima, primero se vuelve
     * al pager. Al tocar la barra se salta directo a la página destino; el gesto
     * de swipe horizontal con el dedo sigue animando.
     */
    fun selectTab(index: Int) {
        scope.launch {
            navController.popBackStack(Routes.TABS, inclusive = false)
            if (pagerState.currentPage != index) {
                pagerState.scrollToPage(index)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AppBottomBar(
                items = TabDestinations.map { BottomBarItem(it.label, it.icon) },
                selectedIndex = if (onTabs) pagerState.currentPage else -1,
                onSelect = ::selectTab,
                onAdd = { navController.navigate(Routes.record(false)) },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TABS,
            modifier = Modifier.padding(innerPadding),
            // El pager ya anima el cambio de destino, así que las transiciones
            // del NavHost son solo para lo que se empuja encima.
            enterTransition = { PushEnterTransition },
            exitTransition = { PushExitTransition },
            popEnterTransition = { PopEnterTransition },
            popExitTransition = { PopExitTransition },
        ) {
            composable(Routes.TABS) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
                ) {
                    TabPager(
                        pagerState = pagerState,
                        navController = navController,
                    )
                    dev.tubolsillo.app.ui.assistant.AssistantOverlay()
                }
            }
            composable(Routes.CATEGORIES) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.REVIEW) {
                val draftHolder: EntryDraftHolder = koinInject()
                ReviewScreen(
                    onBack = { navController.popBackStack() },
                    onUseReading = { draft ->
                        draftHolder.put(draft)
                        navController.navigate(Routes.record(draft.income))
                    },
                )
            }
            composable(Routes.PLAN) {
                PlanScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.RECORD,
                arguments = listOf(navArgument("income") { type = NavType.IntType }),
            ) { entry ->
                NewRecordScreen(
                    initialIncome = (entry.arguments?.getInt("income") ?: 0) == 1,
                    onDone = { navController.popBackStack() },
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
            composable(Routes.PORTAL) {
                PortalScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.AI_MODEL) {
                AiModelScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CLASSIFIER) {
                ClassifierScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CREDITS) {
                CreditsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAccount = { id -> navController.navigate(Routes.account(id)) },
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
        }
    }
}

/**
 * Los cuatro destinos en un pager: se mantienen pre-compuestos para que
 * deslizar o tocar sea instantáneo.
 */
@Composable
private fun TabPager(
    pagerState: PagerState,
    navController: NavHostController,
) {
    // Un solo DashboardViewModel para Inicio y Estadísticas: comparten período y
    // datos, así no hay dos consultas ni dos cálculos para lo mismo.
    val dashboardViewModel: DashboardViewModel = koinViewModel()

    HorizontalPager(
        state = pagerState,
        // Solo la página visible y una vecina: precomponer las cuatro duplica el
        // trabajo de arranque y de scroll sin que se note.
        beyondViewportPageCount = 1,
        key = { TabDestinations[it].route },
    ) { page ->
        when (TabDestinations[page].route) {
            Routes.HOME -> HomeScreen(
                onAccountClick = { id -> navController.navigate(Routes.account(id)) },
                onNewRecord = { income -> navController.navigate(Routes.record(income)) },
                onOpenPlan = { navController.navigate(Routes.PLAN) },
                onOpenRates = { navController.navigate(Routes.RATES) },
                onOpenCredits = { navController.navigate(Routes.CREDITS) },
                onManageCategories = { navController.navigate(Routes.CATEGORIES) },
                viewModel = dashboardViewModel,
            )

            Routes.RECORDS -> TransactionsScreen()

            Routes.STATS -> StatsScreen(viewModel = dashboardViewModel)

            Routes.MENU -> SettingsScreen(
                onAiModelClick = { navController.navigate(Routes.AI_MODEL) },
                onCreditsClick = { navController.navigate(Routes.CREDITS) },
                onClassifierClick = { navController.navigate(Routes.CLASSIFIER) },
                onRatesClick = { navController.navigate(Routes.RATES) },
                onBinanceClick = { navController.navigate(Routes.BINANCE) },
                onEventsClick = { navController.navigate(Routes.EVENTS) },
                onPortalClick = { navController.navigate(Routes.PORTAL) },
                onReviewClick = { navController.navigate(Routes.REVIEW) },
            )
        }
    }
}
