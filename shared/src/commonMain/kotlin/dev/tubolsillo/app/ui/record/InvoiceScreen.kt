package dev.tubolsillo.app.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.money.Currency
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.core.platform.rememberReceiptScanner
import dev.tubolsillo.app.domain.receipts.ParsedReceipt
import dev.tubolsillo.app.domain.receipts.ReceiptParser
import dev.tubolsillo.app.ui.components.GroupFooter
import dev.tubolsillo.app.ui.components.GroupHeader
import dev.tubolsillo.app.ui.components.GroupSeparator
import dev.tubolsillo.app.ui.components.GroupedGroup
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.components.formatDate
import dev.tubolsillo.app.ui.review.EntryDraft
import dev.tubolsillo.app.ui.theme.TubolsilloColors

/**
 * Vista propia para la factura: se toma la foto, el OCR corre en el teléfono y
 * aquí se ve qué se leyó (comercio, total y fecha) antes de decidir si se usa.
 * El alta manual no se abre hasta que el usuario confirma "Usar estos datos".
 */
@Composable
fun InvoiceScreen(
    onBack: () -> Unit,
    onUse: (EntryDraft) -> Unit,
    onManual: () -> Unit,
) {
    var currencyCode by remember { mutableStateOf("VES") }
    var reading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var parsed by remember { mutableStateOf<ParsedReceipt?>(null) }
    var rawText by remember { mutableStateOf<String?>(null) }
    var showRaw by remember { mutableStateOf(false) }

    val scan = rememberReceiptScanner { text, scanError ->
        reading = false
        error = scanError
        rawText = text
        parsed = if (!text.isNullOrBlank()) {
            runCatching { ReceiptParser.parse(text, currencyCode) }.getOrNull()
        } else null
    }

    IOSScreen(title = "Nueva factura", onBack = onBack) {
        item(key = "intro") {
            GroupFooter(
                "La foto se lee en tu teléfono: el ticket no sale de aquí. " +
                    "Después puedes corregir el monto antes de guardarlo."
            )
        }

        item(key = "scan") {
            GroupedGroup {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = TubolsilloColors.tileFill,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (reading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = if (reading) "Leyendo la factura…" else "Enfoca el ticket completo",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Con el total y la fecha a la vista la app los reconoce sola.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Moneda en la que está el ticket: cambia cómo se lee el total.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Currency.VES.code, Currency.USD.code).forEach { code ->
                            val selected = code == currencyCode
                            Surface(
                                onClick = {
                                    currencyCode = code
                                    rawText?.let { text ->
                                        parsed = runCatching {
                                            ReceiptParser.parse(text, code)
                                        }.getOrNull()
                                    }
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else TubolsilloColors.tileFill,
                            ) {
                                Text(
                                    text = code,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            error = null
                            parsed = null
                            showRaw = false
                            reading = true
                            scan()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(if (parsed == null) "Escanear factura" else "Escanear otra vez")
                    }
                }
            }
        }

        error?.let { message ->
            item(key = "error") {
                GroupFooter("No pude leer la foto: " + message)
            }
        }

        parsed?.let { receipt ->
            val hasData = receipt.totalMinor != null || receipt.merchant != null
            if (hasData) {
                item(key = "read-h") { GroupHeader("Lo que leí") }
                item(key = "read") {
                    GroupedGroup {
                        receipt.merchant?.let { merchant ->
                            ReceiptRow("Comercio", merchant)
                            GroupSeparator()
                        }
                        receipt.totalMinor?.let { total ->
                            ReceiptRow(
                                label = "Total",
                                value = MoneyFormatter.formatMinor(total, currencyCode),
                            )
                            GroupSeparator()
                        }
                        receipt.occurredAt?.let { date ->
                            ReceiptRow("Fecha", formatDate(date))
                        }
                    }
                }
                if (!receipt.totalExplained && receipt.totalMinor != null) {
                    item(key = "read-note") {
                        GroupFooter(
                            "El total salió del monto más alto del ticket, no de una línea " +
                                "\"TOTAL\". Revísalo antes de guardar."
                        )
                    }
                }
                item(key = "use") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Button(
                            onClick = {
                                val total = receipt.totalMinor ?: 0L
                                val whole = total / 100
                                val cents = (total % 100).toString().padStart(2, '0')
                                onUse(
                                    EntryDraft(
                                        amount = if (whole == 0L && total == 0L) ""
                                        else "$whole,$cents",
                                        description = receipt.merchant ?: "Factura",
                                        income = false,
                                    )
                                )
                            },
                            enabled = receipt.totalMinor != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Usar estos datos") }
                        if (rawText != null) {
                            TextButton(
                                onClick = { showRaw = !showRaw },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (showRaw) "Ocultar texto leído" else "Ver texto leído") }
                        }
                        if (showRaw) {
                            Text(
                                text = rawText.orEmpty().take(1200),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                item(key = "empty") {
                    GroupFooter(
                        "No reconocí el total en el ticket. Puedes intentar otra foto o " +
                            "escribir el monto a mano."
                    )
                }
            }
        }

        item(key = "manual") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton(
                    onClick = onManual,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Escribir a mano") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
        )
    }
}
