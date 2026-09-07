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

    /** Sesión del portal de un banco (cookies + url de consulta), cifrada. */
    fun savePortalSession(institutionKey: String, cookies: String, url: String)
    fun portalSession(institutionKey: String): Pair<String, String>?
    fun clearPortalSession(institutionKey: String)
}
