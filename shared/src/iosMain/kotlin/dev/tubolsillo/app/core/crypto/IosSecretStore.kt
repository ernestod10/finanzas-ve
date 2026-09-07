package dev.tubolsillo.app.core.crypto

import platform.Foundation.NSUserDefaults

/**
 * TODO(M6/seguridad): migrar a Keychain (SecItem). Por ahora NSUserDefaults
 * para desbloquear M5 (dashboard funcional en iOS); las llaves Binance en
 * NSUserDefaults NO están cifradas — migrar antes de cualquier release.
 */
class IosSecretStore : SecretStore {

    private val prefs = NSUserDefaults.standardUserDefaults

    override fun saveBinanceKeys(apiKey: String, apiSecret: String) {
        prefs.setObject(apiKey.trim(), forKey = KEY_API)
        prefs.setObject(apiSecret.trim(), forKey = KEY_SECRET)
    }

    override fun binanceKeys(): Pair<String, String>? {
        val key = prefs.stringForKey(KEY_API) ?: return null
        val secret = prefs.stringForKey(KEY_SECRET) ?: return null
        return key to secret
    }

    override fun hasBinanceKeys(): Boolean = binanceKeys() != null

    override fun clearBinanceKeys() {
        prefs.removeObjectForKey(KEY_API)
        prefs.removeObjectForKey(KEY_SECRET)
    }

    override fun savePortalSession(institutionKey: String, cookies: String, url: String) {
        prefs.setObject(cookies, forKey = "portal_cookies_" + institutionKey)
        prefs.setObject(url, forKey = "portal_url_" + institutionKey)
    }

    override fun portalSession(institutionKey: String): Pair<String, String>? {
        val c = prefs.stringForKey("portal_cookies_" + institutionKey) ?: return null
        val u = prefs.stringForKey("portal_url_" + institutionKey) ?: return null
        return c to u
    }

    override fun clearPortalSession(institutionKey: String) {
        prefs.removeObjectForKey("portal_cookies_" + institutionKey)
        prefs.removeObjectForKey("portal_url_" + institutionKey)
    }

    private companion object {
        const val KEY_API = "binance_api_key"
        const val KEY_SECRET = "binance_api_secret"
    }
}
