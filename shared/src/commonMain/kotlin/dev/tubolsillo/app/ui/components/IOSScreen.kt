package dev.tubolsillo.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Alto del bloque de título grande; define cuánto scroll tarda en colapsar. */
private val LargeTitleBlockHeight = 56.dp

/**
 * Pantalla al estilo iOS: barra de navegación glass con el título inline que
 * aparece a medida que el título grande se va con el scroll.
 *
 * Es el patrón de `designing-for-ios.md`: el título grande (34/Bold) vive en el
 * contenido y se colapsa a un título inline centrado (17/Semibold) sobre la
 * barra. El contenido pasa por debajo de la barra, que es lo que hace que el
 * glass tenga sentido.
 */
@Composable
fun IOSScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    // Progreso del colapso: 0 = título grande a la vista, 1 = barra inline.
    val density = LocalDensity.current
    val collapseHeightPx = with(density) { LargeTitleBlockHeight.toPx() }
    val collapse by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            if (listState.firstVisibleItemIndex > 0) 1f
            else (offset / collapseHeightPx).coerceIn(0f, 1f)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "__large_title") {
                LargeTitle(title)
            }
            content()
        }

        // Barra de navegación: glass, título inline centrado, acciones al lado.
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .alpha(((collapse - 0.55f) / 0.45f).coerceIn(0f, 1f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(44.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (onBack != null) {
                        Row(
                            modifier = Modifier.clickableRow(onBack).padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBackIos,
                                contentDescription = "Volver",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.height(15.dp),
                            )
                        }
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

/** Título grande de iOS: 34/Bold, alineado a la izquierda, dentro del scroll. */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
    )
}

/**
 * Acción de texto en el acento, como los botones de una barra de iOS. Sirve
 * tanto dentro de la barra como suelta en el contenido.
 */
@Composable
fun HeaderAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clickableRow(onClick)
            .padding(vertical = 10.dp),
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

/** Reserva de alto al final de una lista para que la barra flotante no tape. */
@Composable
fun ListBottomSpacer() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(12.dp)
    )
}
