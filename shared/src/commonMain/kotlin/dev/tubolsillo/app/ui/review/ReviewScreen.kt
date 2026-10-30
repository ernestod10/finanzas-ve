package dev.tubolsillo.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.db.entity.CaptureEventEntity
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.ai.AnalyzerAvailability
import dev.tubolsillo.app.domain.ai.CaptureReading
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.ParseStatus
import dev.tubolsillo.app.domain.model.SourceType
import dev.tubolsillo.app.ui.components.CardHeader
import dev.tubolsillo.app.ui.components.GroupFooter
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.components.SectionHeader
import dev.tubolsillo.app.ui.components.TubolsilloCard
import dev.tubolsillo.app.ui.components.dayLabel
import dev.tubolsillo.app.ui.components.formatTime
import dev.tubolsillo.app.ui.theme.TubolsilloColors
import org.koin.compose.viewmodel.koinViewModel

/**
 * Cola de revisión de capturas que el parser no pudo leer.
 *
 * Gemini Nano (si el dispositivo lo soporta) actúa como segundo lector: propone
 * monto, moneda, comercio y últimos 4. Nunca escribe solo; el usuario confirma
 * y los datos pasan al alta manual.
 */
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onUseReading: (EntryDraft) -> Unit,
    viewModel: ReviewViewModel = koinViewModel(),
) {
    val events by viewModel.events.collectAsState()
    val availability by viewModel.availability.collectAsState()
    val analyses by viewModel.analyses.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val progress by viewModel.downloadProgress.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val batch by viewModel.batch.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val message by viewModel.message.collectAsState()

    IOSScreen(title = "Por revisar", onBack = onBack) {
        item(key = "analyzer") {
            AnalyzerStatusCard(
                availability = availability,
                progress = progress,
                detail = detail,
                onDownload = viewModel::downloadModel,
            )
        }

        if (availability == AnalyzerAvailability.AVAILABLE && events.isNotEmpty()) {
            item(key = "batch") {
                BatchAnalyzeCard(
                    pending = events.count { it.id !in analyses },
                    batch = batch,
                    onStart = { viewModel.analyzePending() },
                    onCancel = viewModel::cancelBatch,
                )
            }
        }

        if (events.isEmpty()) {
            item(key = "empty") { EmptyReviewState() }
        } else {
            item(key = "count") {
                SectionHeader(
                    title = events.size.toString() + " captura(s) sin leer",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(events, key = { event -> "review-" + event.id }) { event ->
                ReviewCard(
                    event = event,
                    reading = analyses[event.id],
                    analyzing = event.id in busy,
                    availability = availability,
                    onAnalyze = { viewModel.analyze(event) },
                    onUse = { reading -> onUseReading(draftFrom(event, reading, categories)) },
                    onDismiss = { viewModel.dismiss(event) },
                )
            }
            item(key = "footer") {
                GroupFooter(
                    "Las capturas descartadas quedan en el registro crudo, por si hay que reparsearlas."
                )
            }
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeMessage() },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeMessage() }) { Text("Entendido") }
            },
        )
    }
}

@Composable
private fun AnalyzerStatusCard(
    availability: AnalyzerAvailability,
    progress: Float?,
    detail: String?,
    onDownload: () -> Unit,
) {
    TubolsilloCard {
        CardHeader(
            title = "Gemini Nano",
            icon = Icons.Filled.AutoAwesome,
            action = {
                if (availability == AnalyzerAvailability.DOWNLOADABLE) {
                    TextButton(onClick = onDownload) { Text("Descargar") }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        when (availability) {
            AnalyzerAvailability.CHECKING -> Text(
                text = "Verificando el modelo del dispositivo…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AnalyzerAvailability.AVAILABLE -> Text(
                text = "Listo. Puede leer las capturas que el parser no entendió.",
                style = MaterialTheme.typography.bodySmall,
                color = TubolsilloColors.positive,
            )

            AnalyzerAvailability.DOWNLOADABLE -> Text(
                text = "El modelo no está descargado. Son varios GB y los administra Android.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AnalyzerAvailability.DOWNLOADING -> {
                Text(
                    text = "Descargando el modelo…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                )
            }

            AnalyzerAvailability.UNAVAILABLE -> Column {
                Text(
                    text = "No disponible en este dispositivo o región. La cola funciona igual, cargando a mano.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                detail?.let { reason ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchAnalyzeCard(
    pending: Int,
    batch: ReviewViewModel.BatchProgress?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    TubolsilloCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Analizar pendientes",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when {
                        batch != null -> "Analizando " + batch.done + " de " + batch.total + "…"
                        pending == 0 -> "Todas tienen lectura"
                        else -> pending.toString() + " captura(s) sin lectura"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (batch == null) {
                TextButton(onClick = onStart, enabled = pending > 0) { Text("Analizar") }
            } else {
                TextButton(onClick = onCancel) { Text("Cancelar") }
            }
        }
        batch?.let { running ->
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { running.done.toFloat() / running.total },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            )
        }
    }
}

@Composable
private fun EmptyReviewState() {
    TubolsilloCard {
        Text(
            text = "No hay capturas por revisar",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Cuando un SMS o un push llegue en un formato que el parser no reconozca, " +
                "aparece acá para que lo revises.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewCard(
    event: CaptureEventEntity,
    reading: CaptureReading?,
    analyzing: Boolean,
    availability: AnalyzerAvailability,
    onAnalyze: () -> Unit,
    onUse: (CaptureReading) -> Unit,
    onDismiss: () -> Unit,
) {
    TubolsilloCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = SourceType.fromKey(event.source).displayName +
                        event.institution?.let { " · " + Institution.fromKey(it).displayName }
                            .orEmpty(),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = dayLabel(event.capturedAt) + " · " + formatTime(event.capturedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (ParseStatus.fromKey(event.parseStatus) == ParseStatus.FAILED) "Error" else "Sin leer",
                style = MaterialTheme.typography.labelSmall,
                color = if (ParseStatus.fromKey(event.parseStatus) == ParseStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = TubolsilloColors.tileFill,
        ) {
            Text(
                text = event.rawText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(10.dp),
                maxLines = 8,
            )
        }

        if (reading != null) {
            Spacer(Modifier.height(10.dp))
            if (reading.isNoise) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "No es un movimiento (clave, aviso o publicidad).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            } else {
                ReadingBlock(reading)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Revisá los datos: el modelo puede equivocarse. Nada se guarda sin que confirmes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (availability) {
                AnalyzerAvailability.AVAILABLE -> TextButton(
                    onClick = onAnalyze,
                    enabled = !analyzing,
                ) {
                    Text(if (analyzing) "Leyendo…" else if (reading == null) "Analizar con Nano" else "Volver a leer")
                }

                AnalyzerAvailability.DOWNLOADABLE -> Text(
                    text = "Descargá el modelo arriba",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )

                else -> Text(
                    text = "Sin modelo: cargá los datos a mano",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            if (reading != null && !reading.isNoise) {
                TextButton(onClick = { onUse(reading) }) { Text("Usar datos") }
            }
            TextButton(onClick = onDismiss) {
                Text("Descartar", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ReadingBlock(reading: CaptureReading) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Lectura del modelo",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            reading.amount?.let { amount ->
                Text(
                    text = "Monto: " + MoneyFormatter.format(
                        dev.tubolsillo.app.core.money.Money.ofMajor(
                            amount,
                            dev.tubolsillo.app.core.money.Currency.of(reading.currency ?: "VES"),
                        )
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            reading.merchant?.let {
                Text(
                    text = "Comercio: " + it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            reading.last4?.let {
                Text(
                    text = "Termina en: •••• " + it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            reading.kind?.let {
                Text(
                    text = "Tipo: " + it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            reading.category?.let {
                Text(
                    text = "Categoría sugerida: " + it,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** Convierte la lectura en el borrador del alta manual. */
private fun draftFrom(
    event: CaptureEventEntity,
    reading: CaptureReading,
    categories: List<dev.tubolsillo.app.core.db.entity.CategoryEntity>,
): EntryDraft {
    val amount = reading.amount?.let { value ->
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    } ?: ""
    val description = reading.merchant?.takeIf { it.isNotBlank() }
        ?: event.rawText.take(60)
    val income = reading.kind?.let { kind ->
        kind.contains("income", ignoreCase = true) ||
            kind.contains("credit", ignoreCase = true) ||
            kind.contains("deposit", ignoreCase = true)
    } == true
    val categoryId = reading.category?.let { name ->
        categories.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
    }
    return EntryDraft(
        amount = amount,
        description = description,
        income = income,
        categoryId = categoryId,
    )
}
