package dev.tubolsillo.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.money.Currency
import dev.tubolsillo.app.ui.theme.TNUM
import dev.tubolsillo.app.ui.theme.TubolsilloColors

/**
 * Teclado numérico propio, como en la referencia: en un monto no hay letras,
 * y el teclado del sistema tapa media pantalla. La coma es decimal; el resto
 * de la validación la hace `AmountNormalizer` al guardar.
 */
@Composable
fun AmountKeypad(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimals: Int = 2,
) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ",", "0", "⌫")

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    KeypadKey(
                        key = key,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (key) {
                                "⌫" -> if (value.isNotEmpty()) onValueChange(value.dropLast(1))
                                "," -> if (!value.contains(",") && decimals > 0) onValueChange(value + ",")
                                else -> if (value.length < 12) onValueChange(value + key)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(
    key: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(50.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = TubolsilloColors.tileFill,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (key == "⌫") {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Borrar",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = key,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = TNUM,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Texto grande del monto, con el código de moneda debajo. */
@Composable
fun AmountDisplay(
    text: String,
    currencyCode: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text.ifBlank { "0" } + if (text.endsWith(",")) "00" else "",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = TNUM,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        Text(
            text = Currency.of(currencyCode).code,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
