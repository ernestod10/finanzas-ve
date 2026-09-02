package dev.tubolsillo.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.tubolsillo.app.core.money.MoneyFormatter

/**
 * Editor de la tasa VES→USD. Convierte TODO el dashboard: si pones la tasa
 * paralela, el patrimonio neto en USD usa paralela; si pones BCV, usa BCV.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatesScreen(
    onBack: () -> Unit,
    viewModel: RatesViewModel = hiltViewModel(),
) {
    val vesPerDollar by viewModel.vesPerDollar.collectAsState()
    var rateText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Tasa de cambio") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            },
        )
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Bolívares por dólar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Tasa actual: " + MoneyFormatter.formatMinor(
                    (vesPerDollar * 100).toLong(), "VES",
                ) + " por $ 1,00",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Nueva tasa (Bs. por $)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = rateText.isNotBlank(),
                    onClick = { viewModel.saveVesRate(rateText); rateText = "" },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Guardar") }
            }
            Text(
                text = "La tasa la decides tú: BCV, paralelo o la que uses de verdad. " +
                    "Convierte cuentas en bolívares a USD en el saldo total.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
