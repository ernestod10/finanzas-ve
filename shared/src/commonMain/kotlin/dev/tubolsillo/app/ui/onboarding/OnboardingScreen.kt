package dev.tubolsillo.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.ui.dashboard.AddAccountDialog
import dev.tubolsillo.app.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Primer arranque. Tres pasos con el mismo lenguaje visual que Inicio: bloque
 * degradado con el acento, círculos apenas visibles, un antetítulo y una sola
 * acción por pantalla. "Saltar" está siempre arriba.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val pages = listOf(
        OnboardingPage(
            icon = Icons.Filled.Lock,
            eyebrow = "PRIVACIDAD",
            title = "Tus cuentas, solo en tu teléfono",
            body = "TuBolsillo no tiene servidores: los bancos, Binance y Cashea se " +
                "leen en el propio teléfono y nada se sube a internet.",
        ),
        OnboardingPage(
            icon = Icons.Filled.AutoAwesome,
            eyebrow = "AUTOMÁTICO",
            title = "Se registra solo",
            body = "Los SMS y notificaciones de tus bancos se convierten en movimientos, " +
                "y los portales vinculados actualizan saldos al abrir la app.",
        ),
        OnboardingPage(
            icon = Icons.Filled.Wallet,
            eyebrow = "PRIMER PASO",
            title = "Empecemos",
            body = "Crea tu primera cuenta a mano o deja que la app la lea. " +
                "Después puedes agregar tarjetas, deudas y objetivos de ahorro.",
        ),
    )

    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    var showAddAccount by remember { mutableStateOf(false) }
    val lastPage = pager.currentPage == pages.lastIndex

    // Surface fija el color de contenido: sin él los Text heredaban negro
    // sobre el fondo oscuro y el título se leía mal.
    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = (pager.currentPage + 1).toString() + " de " + pages.size,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
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
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    HeroPanel(page.icon)
                    Spacer(Modifier.height(28.dp))
                    Text(
                        text = page.eyebrow,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
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

            // Píldoras en vez de puntos: la activa se estira y se colorea.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { index ->
                    val active = pager.currentPage == index
                    val width by animateDpAsState(if (active) 22.dp else 7.dp, label = "dot")
                    val color by animateColorAsState(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        label = "dotColor",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = width, height = 7.dp)
                            .background(color, CircleShape),
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                if (lastPage) {
                    Button(
                        onClick = { showAddAccount = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) { Text("Crear mi primera cuenta", fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Empezar con la app") }
                } else {
                    Button(
                        onClick = {
                            scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) { Text("Siguiente", fontWeight = FontWeight.SemiBold) }
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

/**
 * Bloque degradado con el icono grande: mismo recurso que el saludo de Inicio
 * (acento → suave) más dos círculos que dan profundidad sin textura.
 */
@Composable
private fun HeroPanel(icon: ImageVector) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val primary = MaterialTheme.colorScheme.primary
    val start = if (dark) MaterialTheme.colorScheme.primaryContainer else primary
    val end = if (dark) lerp(MaterialTheme.colorScheme.primaryContainer, primary, 0.35f)
    else lerp(primary, Color.White, 0.24f)
    val ink = if (dark) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(236.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(start, end))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-30).dp)
                .size(150.dp)
                .background(ink.copy(alpha = 0.08f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-34).dp, y = 26.dp)
                .size(120.dp)
                .background(ink.copy(alpha = 0.06f), CircleShape),
        )
        Surface(
            shape = CircleShape,
            color = ink.copy(alpha = 0.16f),
            modifier = Modifier.size(112.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val eyebrow: String,
    val title: String,
    val body: String,
)
