package dev.tubolsillo.app.core.platform

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberCsvImportPicker(onResult: FileResult): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(null, null)
        } else {
            onResult(readContent(context, uri), uri.lastPathSegment)
        }
    }
    return { launcher.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values")) }
}

@Composable
actual fun rememberJsonImportPicker(onResult: FileResult): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(null, null)
        } else {
            onResult(readContent(context, uri), uri.lastPathSegment)
        }
    }
    return { launcher.launch(arrayOf("application/json", "text/*", "*/*")) }
}

@Composable
actual fun rememberJsonExportPicker(defaultName: String): (String) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        exportPendingJson?.let { json ->
            uri?.let {
                runCatching {
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
            }
            exportPendingJson = null
        }
    }
    return { json: String ->
        exportPendingJson = json
        launcher.launch(defaultName)
    }
}

/** JSON pendiente entre el botón y la elección del destino en el picker. */
private var exportPendingJson: String? = null

private fun readContent(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

@Composable
actual fun rememberModelImportPicker(onResult: (path: String?, name: String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(null, null)
            return@rememberLauncherForActivityResult
        }
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "modelo.task"
        val target = java.io.File(java.io.File(context.filesDir, "models").apply { mkdirs() }, name)
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.takeIf { it.length() > 0 }
        }.getOrNull()
        onResult(copied?.absolutePath, name)
    }
    return { launcher.launch(arrayOf("*/*")) }
}
