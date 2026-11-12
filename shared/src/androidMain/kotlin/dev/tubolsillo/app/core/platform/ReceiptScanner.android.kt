package dev.tubolsillo.app.core.platform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
actual fun rememberReceiptScanner(onResult: (text: String?, error: String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(null, null) // el usuario canceló
            return@rememberLauncherForActivityResult
        }
        // El OCR corre en un hilo propio del SDK; aquí solo se espera.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val text = runCatching {
                val image = InputImage.fromFilePath(context, uri)
                suspendCancellableCoroutine { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { result -> cont.resume(result.text) }
                        .addOnFailureListener { error -> cont.resume(null) }
                }
            }.getOrNull()
            withContextMain { onResult(text, if (text.isNullOrBlank()) "No pude leer texto en la foto." else null) }
        }
    }

    return {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

private suspend fun withContextMain(block: () -> Unit) =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { block() }
