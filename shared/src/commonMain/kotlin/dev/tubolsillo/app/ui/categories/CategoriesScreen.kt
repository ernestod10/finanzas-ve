package dev.tubolsillo.app.ui.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.db.entity.CategoryEntity
import dev.tubolsillo.app.ui.components.ACCOUNT_PALETTE
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.components.parseAccountColor
import org.koin.compose.viewmodel.koinViewModel

/** Gestión de categorías del presupuesto: agregar, renombrar, recolorear, borrar. */
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = koinViewModel(),
) {
    val categories by viewModel.categories.collectAsState()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    IOSScreen(
        title = "Categorías",
        onBack = onBack,
        actions = {
            dev.tubolsillo.app.ui.components.HeaderAction("Nueva") { creating = true }
        },
    ) {
        item(key = "intro", contentType = "intro") {
            dev.tubolsillo.app.ui.components.GroupFooter(
                "Las categorías definen tu presupuesto mensual. Toca cualquiera para editar su nombre o color."
            )
        }
        if (categories.isEmpty()) {
            item(key = "empty") {
                dev.tubolsillo.app.ui.components.GroupFooter("Aún no tienes categorías. Toca «Nueva» arriba para crear una.")
            }
        } else {
            item(key = "list", contentType = "group") {
                dev.tubolsillo.app.ui.components.GroupedGroup {
                    categories.forEachIndexed { index, category ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editing = category }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier.size(18.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = parseAccountColor(category.colorHex)
                                            ?: MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp),
                                    ) {}
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { viewModel.delete(category.id) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Eliminar",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            if (index != categories.lastIndex) {
                                androidx.compose.material3.HorizontalDivider(
                                    modifier = Modifier.padding(start = 46.dp, end = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        CategoryDialog(
            title = "Nueva categoría",
            initialName = "",
            initialColor = ACCOUNT_PALETTE[0],
            onDismiss = { creating = false },
            onSave = { name, color ->
                viewModel.add(name, color)
                creating = false
            },
        )
    }
    editing?.let { category ->
        CategoryDialog(
            title = "Editar categoría",
            initialName = category.name,
            initialColor = category.colorHex ?: ACCOUNT_PALETTE[0],
            onDismiss = { editing = null },
            onSave = { name, color ->
                viewModel.update(category.id, name, color)
                editing = null
            },
        )
    }
}

@Composable
private fun CategoryDialog(
    title: String,
    initialName: String,
    initialColor: String,
    onDismiss: () -> Unit,
    onSave: (name: String, colorHex: String?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var colorHex by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (ej. Comida)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ACCOUNT_PALETTE.forEach { hex ->
                        val selected = colorHex == hex
                        Surface(
                            onClick = { colorHex = hex },
                            shape = CircleShape,
                            color = parseAccountColor(hex) ?: MaterialTheme.colorScheme.primary,
                            border = if (selected) BorderStroke(2.dp, Color.Black.copy(alpha = 0.6f)) else null,
                            modifier = Modifier.size(if (selected) 30.dp else 26.dp),
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), colorHex) },
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
