package dev.tubolsillo.app.ui.onboarding

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.ui.dashboard.AddAccountDialog
import dev.tubolsillo.app.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Primer arranque: tres pantallas que explican lo importante antes de pedir
 * nada — todo vive en el teléfono, la captura es automática y por dónde
 * empezar. Se puede saltar; al terminar queda marcado en DataStore.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val pages = listOf(
        OnboardingPage(
            icon = Icons.Filled.Lock,
            title = "Tus cuentas, solo en tu teléfono",
            body = "TuBolsillo no tiene servidores: los bancos, Binance y Cashea se " +
                "leen en el propio teléfono y nada se sube a internet.",
        ),
        OnboardingPage(
            icon = Icons.Filled.AutoAwesome,
            title = "Se registra solo",
            body = "Los SMS y notificaciones de tus bancos se convierten en movimientos, " +
                "y los portales vinculados actualizan saldos al abrir la app.",
        ),
        OnboardingPage(
            icon = Icons.Filled.Wallet,
            title = "Empecemos",
            body = "Crea tu primera cuenta a mano o deja que la app la lea. " +
                "Después puedes agregar tarjetas, deudas y objetivos de ahorro.",
        ),
    )

    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    var showAddAccount by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, end = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDone) {
                    Text("Saltar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
            ) { index ->
                val page = pages[index]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.size(96.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = page.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = page.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (pager.currentPage == index) 9.dp else 7.dp)
                            .background(
                                color = if (pager.currentPage == index) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                if (pager.currentPage == pages.lastIndex) {
                    Button(
                        onClick = { showAddAccount = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Crear mi primera cuenta") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Empezar con la app") }
                } else {
                    Button(
                        onClick = {
                            scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Siguiente") }
                }
                Spacer(Modifier.height(20.dp))
            }
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
                onDone()
            },
        )
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
)
