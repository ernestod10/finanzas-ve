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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.core.di.sharedKoin
import dev.tubolsillo.app.domain.model.Institution

/**
 * Vinculación por portal con AUTO-LOGIN asistido:
 * - Si hay credenciales guardadas, rellena y envía el formulario de login
 *   solo (una vez por página, nunca en bucle).
 * - Si aparece 2FA/coordenadas/pregunta/captcha, se detiene: el usuario
 *   resuelve en pantalla y luego guarda la página de movimientos.
 */
class PortalConnectActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val institutionKey = intent.getStringExtra("institution") ?: "MERCANTIL_PA"
        val institution = Institution.fromKey(institutionKey)
        val secretStore = sharedKoin<SecretStore>()
        val savedCreds = secretStore.portalCredentials(institutionKey)
        val startUrl = institution.portalStartUrl ?: "https://www.mercantilbanco.com.pa/"

        setContent {
            MaterialTheme {
                var status by remember {
                    mutableStateOf(
                        if (savedCreds != null)
                            "Credenciales guardadas: auto-login activo. Si pide 2FA, resuélvelo aquí."
                        else "Inicia sesión; opcionalmente guarda credenciales para auto-login."
                    )
                }
                var urlText by remember { mutableStateOf(startUrl) }
                var user by remember { mutableStateOf(savedCreds?.first ?: "") }
                var pass by remember { mutableStateOf(savedCreds?.second ?: "") }

                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Text(
                        text = "Conectar " + institution.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(8.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { webViewRef?.loadUrl(urlText) }) { Text("Ir") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it },
                            label = { Text("Usuario") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it },
                            label = { Text("Clave") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                if (user.isNotBlank() && pass.isNotBlank()) {
                                    secretStore.savePortalCredentials(institutionKey, user, pass)
                                    autoLogin()
                                    status = "Credenciales guardadas. Auto-login enviado."
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Guardar y auto-login") }
                        Button(onClick = { autoLogin() }, modifier = Modifier.weight(1f)) {
                            Text("Auto-login")
                        }
                    }
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
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        if (autoLoginAttempts < MAX_AUTO_LOGIN_ATTEMPTS) {
                                            autoLogin()
                                        }
                                    }
                                }
                                loadUrl(startUrl)
                                webViewRef = this
                                portalKey = institutionKey
                                portalSecret = secretStore
                            }
                        },
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { saveCurrentPage(institutionKey) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Guardar esta página (movimientos)") }
                        TextButton(onClick = { finish() }) { Text("Cerrar") }
                    }
                }
            }
        }
    }

    private var webViewRef: WebView? = null
    private var portalKey: String? = null
    private var portalSecret: SecretStore? = null
    private var autoLoginAttempts = 0

    private companion object {
        const val MAX_AUTO_LOGIN_ATTEMPTS = 3
    }

    /** Rellena y envía el formulario de login (una sola vez por página). */
    private fun autoLogin() {
        val webView = webViewRef ?: return
        val key = portalKey ?: return
        val creds = portalSecret?.portalCredentials(key) ?: return
        autoLoginAttempts++
        val userJs = creds.first.replace("\\", "\\\\").replace("'", "\\'")
        val passJs = creds.second.replace("\\", "\\\\").replace("'", "\\'")
        val script = """
            (function(){
              if (window.__tbAutoLoginDone) return;
              var ins = document.querySelectorAll('input');
              var userEl = null, passEl = null;
              for (var i = 0; i < ins.length; i++) {
                var t = (ins[i].type || '').toLowerCase();
                if (t === 'password') passEl = passEl || ins[i];
                else if (t === 'text' || t === 'email' || t === 'tel' || t === '') userEl = userEl || ins[i];
              }
              if (!userEl || !passEl) return;
              userEl.value = '$userJs';
              passEl.value = '$passJs';
              window.__tbAutoLoginDone = true;
              var f = userEl.form || passEl.form;
              if (f && f.submit) { f.submit(); }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun saveCurrentPage(institutionKey: String) {
        val webView = webViewRef ?: return
        val url = webView.url ?: return
        val cookies = CookieManager.getInstance().getCookie(url)
        if (cookies.isNullOrBlank() || url.length < 40) return
        runCatching { portalSecret?.savePortalSession(institutionKey, cookies, url) }
    }
}
