package dev.tubolsillo.app.portal

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.http.SslError
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import dev.tubolsillo.app.R
import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.core.di.sharedKoin
import dev.tubolsillo.app.data.source.portal.PortalJs
import dev.tubolsillo.app.data.source.portal.PortalRecipe
import dev.tubolsillo.app.data.source.portal.PortalRecipes
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.ui.components.InstitutionLogoBadge
import dev.tubolsillo.app.ui.theme.TubolsilloColors
import dev.tubolsillo.app.ui.theme.TubolsilloTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
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

    private val digiCertEvIntermediate: X509Certificate? by lazy {
        runCatching {
            resources.openRawResource(R.raw.digicert_ev_rsa_ca_g2).use { stream ->
                CertificateFactory.getInstance("X.509").generateCertificate(stream) as X509Certificate
            }
        }.getOrNull()
    }

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
        val appSettings = sharedKoin<dev.tubolsillo.app.domain.repo.SettingsRepository>()
        // En Bancaribe (/bcm/action/web2/...) y BanescOnline (/mantis/Website/Login.aspx),
        // siempre iniciar por portalStartUrl para conservar el flujo de sesión/iframe.
        val url = if (
            institution == Institution.BANCARIBE ||
            institution == Institution.BANESCO ||
            startUrl?.contains("/action/web2/") == true ||
            startUrl?.contains("banesconline.com") == true
        ) {
            institution.portalStartUrl ?: startUrl ?: ""
        } else {
            startUrl ?: institution.portalStartUrl ?: ""
        }

        setContent {
            val darkMode = appSettings.darkMode.value
            val accentKey = appSettings.accentColor.value
            val darkTheme = when (darkMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            TubolsilloTheme(
                darkTheme = darkTheme,
                accent = dev.tubolsillo.app.ui.theme.AppAccent.fromKey(accentKey),
            ) {
                val steps = remember { mutableStateListOf<String>() }
                var urlText by rememberSaveable { mutableStateOf(url) }
                val needsId = recipe.idSelector != null
                val savedUser = secretStore.portalCredentials(institutionKey)?.first ?: ""
                var user by rememberSaveable { mutableStateOf(if (needsId) savedUser.substringBefore('|') else savedUser) }
                var idValue by rememberSaveable { mutableStateOf(if (needsId) savedUser.substringAfter('|', "") else "") }
                var pass by rememberSaveable { mutableStateOf(secretStore.portalCredentials(institutionKey)?.second ?: "") }
                fun storedUser(): String = if (needsId) user.trim() + "|" + idValue.trim() else user
                var busy by remember { mutableStateOf(mode == MODE_FETCH) }
                var showWeb by remember { mutableStateOf(mode == MODE_LINK) }

                fun log(message: String) {
                    android.util.Log.d("PortalWeb", message)
                    steps.add(message)
                    if (steps.size > 12) steps.removeAt(0)
                }

                // Consulta automática: la ventana es translúcida y el contenido
                // (WebView incluido) queda invisible; el aviso se pinta en un
                // popup aparte. Con showWeb (login/2FA) vuelve todo a la vista.
                val compact = !showWeb
                // Mientras corre escondida, esta ventana no debe tragarse los
                // toques: el popup del aviso tiene su propia ventana y sigue
                // funcionando, y el resto va a la app de abajo.
                androidx.compose.runtime.LaunchedEffect(busy, showWeb) {
                    if (busy && !showWeb) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (compact) Color.Transparent else MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (compact) 0f else 1f)
                            .padding(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                InstitutionBadge(institution, size = 34)
                                Text(
                                    text = (if (mode == MODE_LINK) "Vincular " else "Consultar ") +
                                        institution.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            TextButton(
                                onClick = {
                                    // BanescOnline redirige a www.banesco.com si detecta navigation.type === 'reload'.
                                    if (institution == Institution.BANESCO || urlText.contains("banesconline.com")) {
                                        institution.portalStartUrl?.let { start -> webView?.loadUrl(start) }
                                    } else {
                                        webView?.reload()
                                    }
                                }
                            ) {
                                Text("Recargar")
                            }
                        }

                        if (showWeb) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
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
                                    label = { Text(if (needsId) "Nº de tarjeta" else "Usuario") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                if (needsId) {
                                    OutlinedTextField(
                                        value = idValue,
                                        onValueChange = { idValue = it },
                                        label = { Text("Cédula") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
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
                                        if (user.isBlank() || pass.isBlank() || (needsId && idValue.isBlank())) {
                                            log(if (needsId) "Escribe tarjeta, cédula y clave antes de guardar." else "Escribe usuario y clave antes de guardar.")
                                        } else {
                                            secretStore.savePortalCredentials(institutionKey, storedUser(), pass)
                                            log("Credenciales guardadas (cifradas).")
                                            autoLogin(recipe, storedUser(), pass) { log(it) }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Guardar y entrar") }
                                Button(
                                    onClick = {
                                        if (user.isBlank() || pass.isBlank() || (needsId && idValue.isBlank())) log("Faltan credenciales.")
                                        else autoLogin(recipe, storedUser(), pass) { log(it) }
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
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                        )
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.databaseEnabled = true
                                        settings.javaScriptCanOpenWindowsAutomatically = true
                                        settings.setSupportMultipleWindows(false)
                                        settings.loadWithOverviewMode = true
                                        settings.useWideViewPort = true
                                        settings.builtInZoomControls = true
                                        settings.displayZoomControls = false
                                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                        // UA de Chrome Mobile sin la marca de WebView, congelado
                                        // la primera vez: si cambia con cada update del WebView,
                                        // los bancos que atan la confianza al UA ven un equipo
                                        // nuevo en cada consulta.
                                        settings.userAgentString = stableUserAgent(settings)
                                        setBackgroundColor(android.graphics.Color.WHITE)
                                        isFocusable = true
                                        isFocusableInTouchMode = true
                                        CookieManager.getInstance().setAcceptCookie(true)
                                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                if (newProgress == 100) log("Página cargada.")
                                            }
                                        }
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                            ): Boolean {
                                                val target = request?.url ?: return false
                                                val scheme = target.scheme?.lowercase() ?: return false
                                                // Bloquea redirecciones a intent:// o market:// que dejan el WebView en blanco.
                                                if (scheme != "http" && scheme != "https") {
                                                    log("Redirección externa ignorada: $scheme")
                                                    return true
                                                }
                                                return false
                                            }

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
                                                view?.evaluateJavascript(PortalJs.buildProbeScript(recipe), null)
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
                                                handler: SslErrorHandler?,
                                                error: SslError?,
                                            ) {
                                                if (error != null && isVerifiedBanescoIntermediateChain(error)) {
                                                    handler?.proceed()
                                                    return
                                                }
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
                                // La consulta corre escondida: el aviso va en
                                // una ventana que solo ocupa el tamaño de la
                                // tarjeta, para que el resto de la app siga
                                // recibiendo toques (antes un Dialog a pantalla
                                // completa se los comía).
                                androidx.compose.ui.window.Popup(
                                    alignment = Alignment.TopCenter,
                                    properties = androidx.compose.ui.window.PopupProperties(
                                        focusable = false,
                                    ),
                                ) {
                                    androidx.compose.foundation.layout.Box(
                                        modifier = androidx.compose.ui.Modifier
                                            .padding(top = 6.dp, start = 12.dp, end = 12.dp),
                                    ) {
                                        CompactSyncCard(
                                            institution = institution,
                                            message = steps.lastOrNull() ?: "Abriendo el portal…",
                                            onCancel = { finish() },
                                        )
                                    }
                                }
                            }
                        }

                        if (showWeb) {
                            LogSection(steps = steps)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (mode == MODE_LINK || showWeb) {
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
                                                } else if (state == "error_page") {
                                                    log("El portal mostró un error de sesión. Recargando inicio…")
                                                    institution.portalStartUrl?.let { webView?.loadUrl(it) }
                                                } else {
                                                    log("Extrayendo saldo y movimientos de esta pantalla…")
                                                    val json = extractWithFallbackBalance(recipe) { log(it) }
                                                    CookieManager.getInstance().flush()
                                                    val safeUrl = if (
                                                        institution == Institution.BANCARIBE ||
                                                        institution == Institution.BANESCO ||
                                                        institution == Institution.BNC ||
                                                        current.contains("/action/web2/") ||
                                                        current.contains("banesconline.com") ||
                                                        current.contains("bncenlinea.com")
                                                    ) {
                                                        institution.portalStartUrl ?: current
                                                    } else {
                                                        current
                                                    }
                                                    if (mode == MODE_LINK) {
                                                        answer(PortalActivityResult.Linked(safeUrl, json))
                                                    } else {
                                                        answer(
                                                            PortalActivityResult.Payload(
                                                                json ?: "{\"status\":\"empty\",\"rows\":[]}",
                                                                safeUrl,
                                                            )
                                                        )
                                                    }
                                                    finish()
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        if (mode == MODE_LINK) "Guardar e importar pantalla"
                                        else "Importar esta pantalla"
                                    )
                                }
                            }
                            TextButton(onClick = { finish() }) { Text("Cerrar") }
                        }
                    }
                }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    runAutoCaptureLoop(
                        mode = mode,
                        institution = institution,
                        recipe = recipe,
                        credentials = secretStore.portalCredentials(institutionKey),
                        onLog = { log(it) },
                        onRevealWeb = { showWeb = true; busy = false },
                    )
                }
            }
        }
    }

    private val extractor = PortalExtractor { evaluateRaw(it) }

    private suspend fun extractWithFallbackBalance(
        recipe: PortalRecipe,
        onLog: ((String) -> Unit)? = null,
    ): String? = extractor.extract(recipe, onLog)

    /**
     * Banesco Conecta envía su certificado hoja sin adjuntar la CA intermedia
     * DigiCert EV RSA CA G2 en el handshake TLS. Si el WebView dispara
     * SSL_UNTRUSTED en banesco.com, verificamos criptográficamente la firma
     * del certificado hoja contra la CA intermedia oficial empaquetada en res/raw.
     */
    private fun isVerifiedBanescoIntermediateChain(error: SslError): Boolean {
        if (error.primaryError != SslError.SSL_UNTRUSTED) return false
        val host = runCatching { android.net.Uri.parse(error.url).host?.lowercase() }.getOrNull() ?: return false
        if (!host.endsWith("banesco.com") && !host.endsWith("todoticketve.com")) return false
        val intermediate = digiCertEvIntermediate ?: return false
        val x509 = error.certificate?.x509Certificate ?: return false
        return runCatching {
            x509.checkValidity()
            x509.verify(intermediate.publicKey)
            true
        }.getOrDefault(false)
    }

    /** Sondea el DOM: en una SPA onPageFinished no dice nada útil. */
    private suspend fun probe(recipe: PortalRecipe): String {
        val raw = evaluate(PortalJs.buildProbeScript(recipe)) ?: return "unknown"
        return when {
            raw.contains("\"error_page\"") -> "error_page"
            raw.contains("\"expired\"") -> "expired"
            raw.contains("\"login\"") -> "login"
            raw.contains("\"ready\"") -> "ready"
            else -> "unknown"
        }
    }

    private suspend fun runAutoCaptureLoop(
        mode: String,
        institution: Institution,
        recipe: PortalRecipe,
        credentials: Pair<String, String>?,
        onLog: (String) -> Unit,
        onRevealWeb: () -> Unit,
    ) {
        if (mode == MODE_FETCH) {
            onLog("Abriendo el portal…")
        } else {
            onLog("Inicia sesión; al detectar tus movimientos se importarán automáticamente.")
        }
        val initialDeadline = System.currentTimeMillis() + recipe.timeoutMs
        val interactiveDeadline = System.currentTimeMillis() + 300_000L // 5 min mientras esté en pantalla
        var autoNavAttempts = 0
        var idleReadyPolls = 0
        var triedLogin = false
        var reloadedFromError = false
        var revealedForNavigation = mode == MODE_LINK

        while (System.currentTimeMillis() < if (revealedForNavigation) interactiveDeadline else initialDeadline) {
            when (probe(recipe)) {
                "expired" -> {
                    // El banco cerró la sesión: volvemos al login y el auto-login
                    // retoma con las credenciales guardadas.
                    onLog("La sesión del banco expiró; volviendo a entrar…")
                    institution.portalStartUrl?.let { start -> webView?.loadUrl(start) }
                    kotlinx.coroutines.delay(2_000)
                }

                "error_page" -> {
                    if (!reloadedFromError && institution.portalStartUrl != null) {
                        reloadedFromError = true
                        onLog("Sesión expirada en esa ruta; abriendo inicio del banco…")
                        webView?.loadUrl(institution.portalStartUrl!!)
                    } else if (!revealedForNavigation) {
                        revealedForNavigation = true
                        onRevealWeb()
                    }
                }
                "login" -> {
                    if (mode == MODE_FETCH && credentials != null && !triedLogin) {
                        triedLogin = true
                        onLog("Iniciando sesión con tus credenciales guardadas…")
                        val res = evaluate(PortalJs.buildLoginScript(recipe, credentials.first, credentials.second))
                        if (res != null && res.contains("\"step1\"")) {
                            onLog("Usuario enviado; esperando campo de clave…")
                            for (attempt in 1..8) {
                                delay(950)
                                val probeRaw = evaluate(PortalJs.buildProbeScript(recipe)) ?: ""
                                if (probeRaw.contains("\"step\":\"pass\"")) {
                                    val step2 = evaluate(PortalJs.buildLoginScript(recipe, credentials.first, credentials.second))
                                    if (step2 != null && step2.contains("\"ok\"")) {
                                        onLog("Clave enviada; abriendo cuentas…")
                                        break
                                    }
                                }
                            }
                        }
                        delay(1_500)
                    } else if (!revealedForNavigation) {
                        revealedForNavigation = true
                        onLog("El portal pide iniciar sesión: complétalo aquí.")
                        onRevealWeb()
                    }
                }
                "ready" -> {
                    CookieManager.getInstance().flush()
                    delay(if (autoNavAttempts == 0) recipe.settleMs else 900L)
                    val json = extractWithFallbackBalance(recipe, onLog)
                    val payload = json?.let { runCatching { dev.tubolsillo.app.data.source.portal.PortalJson.decode(it) }.getOrNull() }
                    if (payload != null && payload.status == "ok" && payload.rows.isNotEmpty()) {
                        onLog("¡Movimientos y saldos detectados! Importando automáticamente…")
                        val currentUrl = webView?.url ?: ""
                        val safeUrl = if (
                            institution == Institution.BANCARIBE ||
                            institution == Institution.BANESCO ||
                            institution == Institution.BNC ||
                            currentUrl.contains("/action/web2/") ||
                            currentUrl.contains("banesconline.com") ||
                            currentUrl.contains("bncenlinea.com")
                        ) {
                            institution.portalStartUrl ?: currentUrl
                        } else {
                            currentUrl
                        }
                        if (mode == MODE_LINK) {
                            answer(PortalActivityResult.Linked(safeUrl, json))
                        } else {
                            answer(PortalActivityResult.Payload(json, safeUrl))
                        }
                        finish()
                        return
                    }
                    // Aún no hay filas de movimientos en pantalla: intentar navegar automáticamente
                    // desde Posición Consolidada / Directorio de Productos -> Cuenta -> Consultar Movimientos.
                    if (autoNavAttempts < 6) {
                        val navJson = evaluate(PortalJs.buildAutoNavigateScript(recipe))
                        android.util.Log.d("PortalWeb", "autoNav($autoNavAttempts): $navJson")
                        if (navJson != null && navJson.contains("\"status\":\"navigated\"")) {
                            autoNavAttempts += 1
                            onLog("Abriendo movimientos automáticamente (paso $autoNavAttempts)…")
                            delay(1_700)
                            continue
                        }
                    }
                    // Si ya navegamos a la cuenta pero no tiene movimientos en el corte actual,
                    // y ya capturamos los saldos del portal, guardar saldos automáticamente.
                    if (autoNavAttempts >= 1 && json != null && extractor.hasSeenBalances()) {
                        onLog("¡Saldos actualizados! Guardando…")
                        val currentUrl = webView?.url ?: ""
                        val safeUrl = if (
                            institution == Institution.BANCARIBE ||
                            institution == Institution.BANESCO ||
                            institution == Institution.BNC ||
                            currentUrl.contains("/action/web2/") ||
                            currentUrl.contains("banesconline.com") ||
                            currentUrl.contains("bncenlinea.com")
                        ) {
                            institution.portalStartUrl ?: currentUrl
                        } else {
                            currentUrl
                        }
                        if (mode == MODE_LINK) {
                            answer(PortalActivityResult.Linked(safeUrl, json))
                        } else {
                            answer(PortalActivityResult.Payload(json, safeUrl))
                        }
                        finish()
                        return
                    }
                    idleReadyPolls += 1
                    if (idleReadyPolls >= 2 && !revealedForNavigation) {
                        revealedForNavigation = true
                        onLog(
                            "Sesión activa: entra a tu cuenta o pulsa Consultar; se importará solo al cargar."
                        )
                        onRevealWeb()
                    }
                }
            }
            delay(recipe.pollMs)
        }
        if (!revealedForNavigation) {
            answer(PortalActivityResult.NeedsRelink)
            finish()
        }
    }

    /** Rellena y envía el login (soporta login en 1 o 2 pasos, incluyendo iframes de BanescOnline). */
    private fun autoLogin(recipe: PortalRecipe, user: String, pass: String, onLog: (String) -> Unit) {
        lifecycleScope.launch {
            val result = evaluate(PortalJs.buildLoginScript(recipe, user, pass))
            if (result != null && result.contains("\"step1\"")) {
                onLog("Usuario enviado, esperando paso de clave…")
                var step2Result: String? = null
                for (attempt in 1..8) {
                    delay(950)
                    val probeRaw = evaluate(PortalJs.buildProbeScript(recipe)) ?: ""
                    if (probeRaw.contains("\"step\":\"pass\"")) {
                        step2Result = evaluate(PortalJs.buildLoginScript(recipe, user, pass))
                        if (step2Result != null && step2Result.contains("\"ok\"")) break
                    }
                }
                onLog(
                    when {
                        step2Result == null -> "Usuario enviado. Completa la clave en pantalla."
                        step2Result.contains("\"error\"") -> "Usuario enviado. Introduce la clave si falta."
                        else -> "Usuario y clave enviados."
                    }
                )
            } else {
                onLog(
                    when {
                        result == null -> "No se pudo ejecutar el login en la página."
                        result.contains("\"error\"") -> "El formulario no se pudo completar: " + result
                        else -> "Login enviado."
                    }
                )
            }
            // El banco suele renovar la cookie de "equipo frecuente" al loguear;
            // se persiste ya, no recién al cerrar la activity.
            CookieManager.getInstance().flush()
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

    override fun onPause() {
        // Persistir la sesión también al salir a segundo plano, no solo al cerrar.
        CookieManager.getInstance().flush()
        super.onPause()
    }

    /**
     * UA de Chrome Mobile sin la marca de WebView, congelado la primera vez.
     * Si el UA cambia con cada actualización del WebView, los bancos que atan la
     * confianza al dispositivo lo tratan como un equipo nuevo.
     */
    private fun stableUserAgent(settings: WebSettings): String {
        val prefs = getSharedPreferences("portal_ua", MODE_PRIVATE)
        prefs.getString("ua", null)?.let { return it }
        val ua = settings.userAgentString
            .replace("; wv)", ")")
            .replace("Version/4.0 ", "")
        prefs.edit().putString("ua", ua).apply()
        return ua
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

/** Iniciales del banco en su color de marca, con tinta elegida por contraste. */
@Composable
private fun InstitutionBadge(institution: Institution, size: Int = 34) {
    InstitutionLogoBadge(
        institutionKey = institution.key,
        displayName = institution.displayName,
        size = size,
    )
}

/**
 * Pantalla de carga del banco: identidad de marca, qué está pasando en
 * lenguaje humano y una barra de progreso. Los detalles técnicos (que antes
 * ocupaban el pie con líneas crudas) quedan plegados.
 */
@Composable
private fun PortalLoadingOverlay(institution: Institution, steps: List<String>) {
    var showDetails by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                InstitutionBadge(institution, size = 84)

                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Conectando con " + institution.displayName,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Estamos leyendo tus movimientos.\nNo cierres esta pantalla.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(180.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = colors.primary,
                    trackColor = colors.primaryContainer,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = friendlyPhase(steps.lastOrNull()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(2.dp))
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "Ocultar detalles" else "Ver detalles")
                }
                if (showDetails) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = TubolsilloColors.tileFill,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            steps.takeLast(10).forEach { step ->
                                Text(
                                    text = step,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Registro técnico plegado, disponible mientras el usuario navega el portal. */
@Composable
private fun LogSection(steps: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Registro",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Ocultar" else "Detalles")
        }
    }
    if (expanded) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = TubolsilloColors.tileFill,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                steps.takeLast(8).forEach { step ->
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Traduce el último paso técnico a algo que el usuario entiende. */
private fun friendlyPhase(step: String?): String = when {
    step == null -> "Preparando la sesión"
    step.contains("Abriendo", ignoreCase = true) -> "Abriendo el portal del banco"
    step.contains("credencial", ignoreCase = true) ||
        step.contains("clave", ignoreCase = true) ||
        step.contains("sesión", ignoreCase = true) ||
        step.contains("login", ignoreCase = true) -> "Iniciando sesión"
    step.contains("Página cargada", ignoreCase = true) ||
        step.contains("Extrayendo", ignoreCase = true) -> "Leyendo tus movimientos"
    step.contains("Error", ignoreCase = true) ||
        step.contains("respondió", ignoreCase = true) -> "Revisando un aviso del banco"
    else -> "Preparando la sesión"
}

/**
 * Aviso de la consulta automática: tarjeta con el logo del banco y el paso
 * actual, para no bloquear la pantalla. La actividad completa solo aparece
 * cuando el portal pide una acción.
 */
@Composable
private fun CompactSyncCard(
    institution: Institution,
    message: String,
    onCancel: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InstitutionBadge(institution, size = 28)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Consultando " + institution.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onCancel) { Text("Cerrar") }
        }
    }
}
