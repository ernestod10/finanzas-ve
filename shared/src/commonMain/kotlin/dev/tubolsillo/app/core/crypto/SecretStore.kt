package dev.tubolsillo.app.core.crypto

/**
 * Secretos cifrados por plataforma: llaves de API de Binance (y a futuro la
 * contraseña de aplicación del correo).
 * Android: EncryptedSharedPreferences (Keystore AES256-GCM).
 * iOS (M5): Keychain (SecItem).
 */
interface SecretStore {
    fun saveBinanceKeys(apiKey: String, apiSecret: String)
    fun binanceKeys(): Pair<String, String>?
    fun hasBinanceKeys(): Boolean
    fun clearBinanceKeys()

    /** Credenciales para auto-login del portal (opcional), cifradas. */
    fun savePortalCredentials(institutionKey: String, user: String, password: String)
    fun portalCredentials(institutionKey: String): Pair<String, String>?
    fun clearPortalCredentials(institutionKey: String)

    /**
     * Vínculo con el portal de un banco: solo la URL de movimientos. Las
     * cookies YA NO se guardan: vive en el CookieManager del WebView, que las
     * envía con el User-Agent y el Referer reales (un cliente HTTP con la
     * cookie copiada lo rechaza el WAF del banco).
     */
    fun savePortalLink(institutionKey: String, link: PortalLink)
    fun portalLink(institutionKey: String): PortalLink?
    fun clearPortalLink(institutionKey: String)
}

/** Estado de vinculación de un portal. */
data class PortalLink(
    val movementsUrl: String,
    val linkedAtMs: Long,
    val lastSyncAtMs: Long? = null,
)
