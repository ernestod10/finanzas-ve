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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.money.AmountCalculation
import dev.tubolsillo.app.core.money.Currency
import dev.tubolsillo.app.ui.theme.TNUM
import dev.tubolsillo.app.ui.theme.TubolsilloColors

/**
 * Teclado numérico propio, como en la referencia: en un monto no hay letras,
 * y el teclado del sistema tapa media pantalla. La coma es decimal; el resto
 * de la validación la hace `AmountNormalizer` al guardar.
 *
 * Incluye calculadora (+ − × ÷ y =) porque el monto exacto muchas veces es una
 * cuenta: "3 × 1200", "12500 − 3000". El resultado se escribe en la misma
 * pantalla; lo que se guarda sigue siendo el texto de [value].
 */
@Composable
fun AmountKeypad(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimals: Int = 2,
) {
    var calc by remember { mutableStateOf(AmountCalculation(display = value)) }

    // El monto también puede cambiar desde afuera (escáner de factura).
    LaunchedEffect(value) {
        if (value != calc.display) calc = AmountCalculation(display = value)
    }

    fun emit(next: AmountCalculation) {
        calc = next
        onValueChange(next.display)
    }

    val rows = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf(",", "0", "⌫", "+"),
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    KeypadKey(
                        key = key,
                        modifier = Modifier.weight(1f),
                        accent = key in setOf("÷", "×", "−", "+"),
                        onClick = {
                            when (key) {
                                "⌫" -> emit(calc.backspace())
                                "÷" -> emit(calc.withOperator(AmountCalculation.Operator.DIVIDE, decimals))
                                "×" -> emit(calc.withOperator(AmountCalculation.Operator.TIMES, decimals))
                                "−" -> emit(calc.withOperator(AmountCalculation.Operator.MINUS, decimals))
                                "+" -> emit(calc.withOperator(AmountCalculation.Operator.PLUS, decimals))
                                else -> emit(calc.press(key.first(), decimals))
                            }
                        },
                    )
                }
            }
        }
        // "=" está debajo: sin operador pendiente no hace nada, así el teclado
        // no agrega un paso obligatorio para quien solo escribe un monto.
        KeypadKey(
            key = "=",
            modifier = Modifier.fillMaxWidth(),
            accent = true,
            onClick = { emit(calc.evaluate(decimals)) },
        )
    }
}

@Composable
private fun KeypadKey(
    key: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(50.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (accent) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            TubolsilloColors.tileFill
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (key) {
                "⌫" -> Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Borrar",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                else -> Text(
                    text = key,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = TNUM,
                    ),
                    color = if (accent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
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
        // "109243" (lo que se escribe) → "109.243" (lo que se lee).
        val shown = text.ifBlank { "0" } + if (text.endsWith(",")) "00" else ""
        val grouped = shown.substringBefore(',').let { whole ->
            val digits = whole.filter { it.isDigit() }
            if (digits.length <= 3) whole else digits.reversed().chunked(3)
                .joinToString(".").reversed()
        } + shown.substringAfter(',', "").let { if (it.isEmpty()) "" else ",$it" }
        Text(
            text = grouped,
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
