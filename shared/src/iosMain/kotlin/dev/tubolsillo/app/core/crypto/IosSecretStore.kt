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

    private companion object {
        const val KEY_API = "binance_api_key"
        const val KEY_SECRET = "binance_api_secret"
    }
}
