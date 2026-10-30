package dev.tubolsillo.app.portal

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.tubolsillo.app.R
import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.core.di.sharedKoin
import dev.tubolsillo.app.core.platform.PortalFetchResult
import dev.tubolsillo.app.data.source.portal.PortalJs
import dev.tubolsillo.app.data.source.portal.PortalJson
import dev.tubolsillo.app.data.source.portal.PortalRecipes
import dev.tubolsillo.app.domain.model.Institution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.coroutines.resume

/**
 * Consulta un portal SIN pantalla: WebView fuera de ventana, dimensionado a
 * mano y con un shim inyectado al inicio de cada documento que hace creer a
 * la página que está visible (visibilityState / requestAnimationFrame), para
 * que las SPA bancarias pinten sus tablas aunque no haya ventana.
 *
 * Solo funciona sin intervención: si el banco pide 2FA o no hay credenciales
 * guardadas devuelve [PortalFetchResult.NeedsRelink].
 */
class HeadlessPortalRunner(private val context: Context) {

    private val digiCertEvIntermediate: X509Certificate? by lazy {
        runCatching {
            context.resources.openRawResource(R.raw.digicert_ev_rsa_ca_g2).use { stream ->
                CertificateFactory.getInstance("X.509").generateCertificate(stream) as X509Certificate
            }
        }.getOrNull()
    }

    suspend fun fetch(institutionKey: String, savedUrl: String?): PortalFetchResult =
        withContext(Dispatchers.Main) {
            val institution = Institution.fromKey(institutionKey)
            val recipe = PortalRecipes.forInstitution(institution)
                ?: return@withContext PortalFetchResult.Failed("Sin receta para " + institution.displayName)
            val credentials = sharedKoin<SecretStore>().portalCredentials(institutionKey)
            val startUrl = if (
                institution == Institution.BANCARIBE ||
                institution == Institution.BANESCO ||
                institution == Institution.BNC ||
                savedUrl?.contains("/action/web2/") == true ||
                savedUrl?.contains("banesconline.com") == true ||
                savedUrl?.contains("bncenlinea.com") == true
            ) {
                institution.portalStartUrl ?: savedUrl
            } else {
                savedUrl ?: institution.portalStartUrl
            } ?: return@withContext PortalFetchResult.Failed("Sin URL de portal")

            val webView = createWebView()
            val extractor = PortalExtractor { script -> evaluateRaw(webView, script) }
            try {
                log(institution, "Abriendo $startUrl (sin pantalla)")
                webView.loadUrl(startUrl)
                withTimeoutOrNull(TIMEOUT_MS) {
                    runLoop(webView, extractor, institution, recipe, credentials, startUrl)
                } ?: PortalFetchResult.Failed("Tiempo agotado consultando " + institution.displayName)
            } catch (e: Exception) {
                PortalFetchResult.Failed(e.message ?: "Error en segundo plano")
            } finally {
                CookieManager.getInstance().flush()
                runCatching {
                    webView.stopLoading()
                    webView.destroy()
                }
            }
        }

    private suspend fun runLoop(
        webView: WebView,
        extractor: PortalExtractor,
        institution: Institution,
        recipe: dev.tubolsillo.app.data.source.portal.PortalRecipe,
        credentials: Pair<String, String>?,
        startUrl: String,
    ): PortalFetchResult {
        var loginAttempts = 0
        var autoNavAttempts = 0
        var idleReadyPolls = 0
        var reloadedFromError = false
        var lastJson: String? = null

        while (true) {
            when (extractor.probe(recipe)) {
                "error_page" -> {
                    if (reloadedFromError) return PortalFetchResult.NeedsRelink
                    reloadedFromError = true
                    log(institution, "Página de error; recargando inicio")
                    webView.loadUrl(startUrl)
                    delay(2_500)
                }
                "login" -> {
                    if (credentials == null || loginAttempts >= 2) {
                        log(institution, "Login requerido sin credenciales/2FA")
                        return PortalFetchResult.NeedsRelink
                    }
                    loginAttempts += 1
                    log(institution, "Iniciando sesión (intento $loginAttempts)")
                    val res = extractor.evaluate(PortalJs.buildLoginScript(recipe, credentials.first, credentials.second))
                    if (res != null && res.contains("\"step1\"")) {
                        for (attempt in 1..12) {
                            delay(1_000)
                            if (extractor.probeRaw(recipe).contains("\"step\":\"pass\"")) {
                                extractor.evaluate(PortalJs.buildLoginScript(recipe, credentials.first, credentials.second))
                                break
                            }
                        }
                    }
                    delay(3_500)
                }
                "ready" -> {
                    delay(if (autoNavAttempts == 0) recipe.settleMs else 900L)
                    val json = extractor.extract(recipe)
                    if (json != null) lastJson = json
                    val payload = json?.let { runCatching { dev.tubolsillo.app.data.source.portal.PortalJson.decode(it) }.getOrNull() }
                    if (payload != null && payload.status == "ok" && payload.rows.isNotEmpty()) {
                        log(institution, "Movimientos detectados (${payload.rows.size} filas)")
                        return success(json, startUrl)
                    }
                    if (autoNavAttempts < 6) {
                        val navJson = extractor.evaluate(PortalJs.buildAutoNavigateScript(recipe))
                        log(institution, "autoNav($autoNavAttempts): $navJson")
                        if (navJson != null && navJson.contains("\"status\":\"navigated\"")) {
                            autoNavAttempts += 1
                            delay(1_700)
                            continue
                        }
                    }
                    if (autoNavAttempts >= 1 && json != null && extractor.hasSeenBalances()) {
                        log(institution, "Solo saldos (sin movimientos nuevos)")
                        return success(json, startUrl)
                    }
                    idleReadyPolls += 1
                    if (idleReadyPolls >= 8) {
                        return if (lastJson != null && extractor.hasSeenBalances()) success(lastJson, startUrl)
                        else PortalFetchResult.Failed("No se encontraron movimientos en " + institution.displayName)
                    }
                }
                else -> Unit
            }
            delay(recipe.pollMs.coerceAtLeast(700L))
        }
    }

    private fun success(json: String, url: String): PortalFetchResult {
        val payload = PortalJson.decode(json) ?: return PortalFetchResult.Failed("Datos ilegibles")
        return PortalFetchResult.Success(payload, url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val webView = WebView(context.applicationContext)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            offscreenPreRaster = true
            userAgentString = userAgentString
                .replace("; wv)", ")")
                .replace("Version/4.0 ", "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme?.lowercase() ?: return false
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                view?.evaluateJavascript(VISIBILITY_SHIM, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(VISIBILITY_SHIM, null)
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                if (error != null && isVerifiedBanescoIntermediateChain(error)) handler?.proceed()
                else handler?.cancel()
            }
        }
        // Sin ventana el WebView mide 0x0 y nada es "visible" para el DOM:
        // se le da a mano el tamaño de una pantalla de teléfono.
        val w = View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, View.MeasureSpec.EXACTLY)
        val h = View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, View.MeasureSpec.EXACTLY)
        webView.measure(w, h)
        webView.layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)
        webView.onResume()
        webView.resumeTimers()
        return webView
    }

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

    private suspend fun evaluateRaw(view: WebView, script: String): String? =
        suspendCancellableCoroutine { cont ->
            view.evaluateJavascript(script) { value -> if (cont.isActive) cont.resume(value) }
        }

    private fun log(institution: Institution, msg: String) {
        android.util.Log.d("PortalHeadless", institution.key + ": " + msg)
    }

    private companion object {
        const val TIMEOUT_MS = 150_000L
        const val VIEW_WIDTH = 1080
        const val VIEW_HEIGHT = 2340

        /** Hace creer a la página que está en primer plano y con frames. */
        val VISIBILITY_SHIM = """
            (function(){
              try {
                if (window.__tbShim) return; window.__tbShim = true;
                Object.defineProperty(document, 'visibilityState', { configurable: true, get: function(){ return 'visible'; } });
                Object.defineProperty(document, 'hidden', { configurable: true, get: function(){ return false; } });
                try { document.hasFocus = function(){ return true; }; } catch (_e) {}
                window.requestAnimationFrame = function(cb){ return setTimeout(function(){ try { cb(performance.now()); } catch (_e) {} }, 16); };
                window.cancelAnimationFrame = function(id){ clearTimeout(id); };
              } catch (e) {}
            })();
        """.trimIndent()
    }
}
