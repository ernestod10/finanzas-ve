package dev.tubolsillo.app.portal

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.core.di.sharedKoin
import dev.tubolsillo.app.data.source.portal.PortalJs
import dev.tubolsillo.app.data.source.portal.PortalRecipe
import dev.tubolsillo.app.data.source.portal.PortalRecipes
import dev.tubolsillo.app.domain.model.Institution
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Navegador del portal, en dos modos:
 *
 * - LINK: WebView visible. El usuario inicia sesión (resuelve su 2FA), navega a
 *   movimientos y guarda la página.
 * - FETCH: abre la URL guardada y sondea el DOM hasta que la sesión está lista;
 *   entonces ejecuta la receta y devuelve el JSON. Si aparece el login, se
 *   MUESTRA para que el usuario escriba su clave y continúa solo.
 *
 * Es una Activity real y no un WebView suelto a propósito: un WebView sin
 * ventana reporta visibilityState='hidden', el navegador throttlea
 * requestAnimationFrame y las tablas que renderizan en diferido nunca aparecen.
 */
class PortalWebActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var requestId: String? = null
    private var answered = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sin FLAG_SECURE: en varios dispositivos (Samsung entre ellos) el
        // WebView acelerado no compone dentro de una ventana segura y se ve
        // TODO negro. La privacidad de la sesión se mantiene con
        // excludeFromRecents en el manifiesto.

        val institutionKey = intent.getStringExtra(EXTRA_INSTITUTION) ?: Institution.OTHER.key
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_LINK
        val startUrl = intent.getStringExtra(EXTRA_URL)
        requestId = intent.getStringExtra(EXTRA_REQUEST)

        val institution = Institution.fromKey(institutionKey)
        val recipe = PortalRecipes.forInstitution(institution) ?: PortalRecipes.GENERIC_TABLE
        val secretStore = sharedKoin<SecretStore>()
        val url = startUrl ?: institution.portalStartUrl ?: ""

        setContent {
            MaterialTheme {
                val steps = remember { mutableStateListOf<String>() }
                var urlText by rememberSaveable { mutableStateOf(url) }
                var user by rememberSaveable { mutableStateOf(secretStore.portalCredentials(institutionKey)?.first ?: "") }
                var pass by rememberSaveable { mutableStateOf(secretStore.portalCredentials(institutionKey)?.second ?: "") }
                var busy by remember { mutableStateOf(mode == MODE_FETCH) }
                var showWeb by remember { mutableStateOf(mode == MODE_LINK) }

                fun log(message: String) {
                    steps.add(message)
                    if (steps.size > 12) steps.removeAt(0)
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Text(
                        text = (if (mode == MODE_LINK) "Vincular " else "Consultar ") + institution.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(8.dp),
                    )

                    if (showWeb) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = urlText,
                                onValueChange = { urlText = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { webView?.loadUrl(urlText) }) { Text("Ir") }
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
                                    if (user.isBlank() || pass.isBlank()) {
                                        log("Escribe usuario y clave antes de guardar.")
                                    } else {
                                        secretStore.savePortalCredentials(institutionKey, user, pass)
                                        log("Credenciales guardadas (cifradas).")
                                        autoLogin(recipe, user, pass) { log(it) }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Guardar y entrar") }
                            Button(
                                onClick = {
                                    if (user.isBlank() || pass.isBlank()) log("Faltan credenciales.")
                                    else autoLogin(recipe, user, pass) { log(it) }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Entrar") }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    settings.setSupportMultipleWindows(false)
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    // Sin esto el WebView arranca en negro y no
                                    // repinta hasta el primer toque.
                                    setBackgroundColor(android.graphics.Color.WHITE)
                                    isFocusableInTouchMode = true
                                    // El User-Agent por defecto es Chrome real: NO tocarlo,
                                    // es justo lo que el WAF del banco espera ver.
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            if (newProgress == 100) log("Página cargada.")
                                        }
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(
                                            view: WebView?,
                                            url: String?,
                                            favicon: android.graphics.Bitmap?,
                                        ) {
                                            log("Cargando " + (url ?: "").take(60) + "…")
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            view?.invalidate()
                                            url?.let { urlText = it }
                                        }

                                        override fun onReceivedHttpError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            errorResponse: android.webkit.WebResourceResponse?,
                                        ) {
                                            if (request?.isForMainFrame == true) {
                                                log("El portal respondió " + (errorResponse?.statusCode ?: 0))
                                            }
                                        }

                                        override fun onReceivedSslError(
                                            view: WebView?,
                                            handler: android.webkit.SslErrorHandler?,
                                            error: android.net.http.SslError?,
                                        ) {
                                            // No se ignoran: un portal bancario con TLS roto no se abre.
                                            log("Error de certificado: " + (error?.primaryError ?: -1))
                                            handler?.cancel()
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?,
                                        ) {
                                            if (request?.isForMainFrame == true) {
                                                log("Error de carga: " + (error?.description ?: "desconocido"))
                                            }
                                        }

                                        override fun onRenderProcessGone(
                                            view: WebView?,
                                            detail: RenderProcessGoneDetail?,
                                        ): Boolean {
                                            answer(PortalActivityResult.Failed("El navegador del portal se cerró solo."))
                                            finish()
                                            return true
                                        }
                                    }
                                    webView = this
                                    resumeTimers()
                                    onResume()
                                    log("Abriendo " + url.take(60))
                                    loadUrl(url)
                                }
                            },
                        )
                        if (busy && !showWeb) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background,
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    CircularProgressIndicator()
                                    Text(
                                        text = "Consultando " + institution.displayName + "…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 12.dp),
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                    ) {
                        steps.takeLast(4).forEach { step ->
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        if (mode == MODE_LINK) {
                            Button(
                                onClick = {
                                    val current = webView?.url
                                    if (current.isNullOrBlank()) {
                                        log("Aún no hay página cargada.")
                                    } else {
                                        lifecycleScope.launch {
                                            val state = probe(recipe)
                                            if (state == "login") {
                                                log("Esa página es el login: entra primero y ve a movimientos.")
                                            } else {
                                                CookieManager.getInstance().flush()
                                                answer(PortalActivityResult.Linked(current))
                                                finish()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Guardar esta página") }
                        }
                        TextButton(onClick = { finish() }) { Text("Cerrar") }
                    }
                }
                }

                if (mode == MODE_FETCH) {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        runFetch(
                            recipe = recipe,
                            credentials = secretStore.portalCredentials(institutionKey),
                            onLog = { log(it) },
                            onRevealWeb = { showWeb = true; busy = false },
                        )
                    }
                }
            }
        }
    }

    /** Sondea el DOM: en una SPA onPageFinished no dice nada útil. */
    private suspend fun probe(recipe: PortalRecipe): String {
        val raw = evaluate(PortalJs.buildProbeScript(recipe)) ?: return "unknown"
        return when {
            raw.contains("\"login\"") -> "login"
            raw.contains("\"ready\"") -> "ready"
            else -> "unknown"
        }
    }

    private suspend fun runFetch(
        recipe: PortalRecipe,
        credentials: Pair<String, String>?,
        onLog: (String) -> Unit,
        onRevealWeb: () -> Unit,
    ) {
        onLog("Abriendo el portal…")
        val deadline = System.currentTimeMillis() + recipe.timeoutMs
        var loggedInSeen = false
        var triedLogin = false
        while (System.currentTimeMillis() < deadline) {
            when (probe(recipe)) {
                "login" -> {
                    if (credentials != null && !triedLogin) {
                        triedLogin = true
                        onLog("Sesión caducada: enviando credenciales guardadas.")
                        evaluate(PortalJs.buildLoginScript(recipe, credentials.first, credentials.second))
                    } else {
                        onLog("El portal pide iniciar sesión: complétalo aquí.")
                        onRevealWeb()
                    }
                }
                "ready" -> {
                    if (!loggedInSeen) {
                        loggedInSeen = true
                        onLog("Sesión activa: leyendo movimientos…")
                        CookieManager.getInstance().flush()
                        delay(recipe.settleMs)
                        val json = PortalJs.unwrapResult(evaluateRaw(PortalJs.buildExtractScript(recipe)))
                        if (json != null && !json.contains("\"status\":\"login\"")) {
                            answer(PortalActivityResult.Payload(json, webView?.url ?: ""))
                            finish()
                            return
                        }
                        loggedInSeen = false
                    }
                }
            }
            delay(recipe.pollMs)
        }
        answer(PortalActivityResult.NeedsRelink)
        finish()
    }

    /** Rellena y envía el login. Solo se dispara desde un botón o un fetch explícito. */
    private fun autoLogin(recipe: PortalRecipe, user: String, pass: String, onLog: (String) -> Unit) {
        lifecycleScope.launch {
            val result = evaluate(PortalJs.buildLoginScript(recipe, user, pass))
            onLog(
                when {
                    result == null -> "No se pudo ejecutar el login en la página."
                    result.contains("\"error\"") -> "El formulario no se pudo completar: " + result
                    else -> "Login enviado."
                }
            )
        }
    }

    private suspend fun evaluate(script: String): String? = PortalJs.unwrapResult(evaluateRaw(script))

    private suspend fun evaluateRaw(script: String): String? = suspendCancellableCoroutine { cont ->
        val view = webView
        if (view == null) {
            cont.resume(null)
        } else {
            view.evaluateJavascript(script) { value -> if (cont.isActive) cont.resume(value) }
        }
    }

    private fun answer(result: PortalActivityResult) {
        val id = requestId ?: return
        if (answered) return
        answered = true
        PortalBroker.complete(id, result)
    }

    override fun onDestroy() {
        // Si el usuario cierra con "atrás", la corrutina que espera debe soltarse.
        answer(PortalActivityResult.Cancelled)
        CookieManager.getInstance().flush()
        webView?.let { view ->
            view.stopLoading()
            view.destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_INSTITUTION = "institution"
        const val EXTRA_MODE = "mode"
        const val EXTRA_URL = "url"
        const val EXTRA_REQUEST = "request"
        const val MODE_LINK = "LINK"
        const val MODE_FETCH = "FETCH"

        fun intent(
            context: Context,
            institutionKey: String,
            mode: String,
            requestId: String,
            url: String? = null,
        ): Intent = Intent(context, PortalWebActivity::class.java)
            .putExtra(EXTRA_INSTITUTION, institutionKey)
            .putExtra(EXTRA_MODE, mode)
            .putExtra(EXTRA_REQUEST, requestId)
            .putExtra(EXTRA_URL, url)
    }
}
