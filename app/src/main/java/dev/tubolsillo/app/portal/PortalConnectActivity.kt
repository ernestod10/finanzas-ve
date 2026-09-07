package dev.tubolsillo.app.portal

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.tubolsillo.app.core.di.sharedKoin
import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.domain.model.Institution

/**
 * Vinculación del portal de un banco: el usuario inicia sesión en un WebView
 * real (resuelve 2FA/coordenadas en pantalla) y, estando dentro, toca
 * "Guardar esta página": la app guarda las cookies + URL actual cifradas.
 * La consulta posterior replica esa URL con las cookies (session-replay).
 */
class PortalConnectActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val institutionKey = intent.getStringExtra("institution") ?: "MERCANTIL_PA"
        val institution = Institution.fromKey(institutionKey)
        val startUrl = institution.portalStartUrl ?: "https://www.mercantilbanco.com.pa/"

        setContent {
            MaterialTheme {
                var status by remember { mutableStateOf("Inicia sesión en el portal y navega hasta ver tus movimientos.") }
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Text(
                        text = "Conectar " + institution.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(8.dp),
                    )
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = WebViewClient()
                                loadUrl(startUrl)
                                webViewRef = this
                            }
                        },
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                saveCurrentPage(institutionKey) { ok ->
                                    status = if (ok) {
                                        "✓ Sesión guardada. Puedes cerrar; la app la usará para consultar."
                                    } else {
                                        "✗ No se pudo guardar. Verifica que estés dentro del portal."
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Guardar esta página") }
                        TextButton(onClick = { finish() }) { Text("Cerrar") }
                    }
                }
            }
        }
    }

    private fun saveCurrentPage(institutionKey: String, onDone: (Boolean) -> Unit) {
        webViewRef?.let { webView ->
            val url = webView.url ?: run { onDone(false); return }
            val cookies = CookieManager.getInstance().getCookie(url)
            if (cookies.isNullOrBlank() || url.length < 40) {
                // Home o página sin sesión: pedimos que navegue dentro del portal.
                onDone(false)
                return
            }
            runCatching {
                sharedKoin<SecretStore>().savePortalSession(institutionKey, cookies, url)
            }.onSuccess { onDone(true) }.onFailure { onDone(false) }
        } ?: onDone(false)
    }

    private var webViewRef: WebView? = null

    override fun onResume() {
        super.onResume()
        // Se re-asigna la referencia al WebView actual al recomponer la UI.
        // (patrón simple: la Activity es de un solo uso por vinculación)
    }
}
