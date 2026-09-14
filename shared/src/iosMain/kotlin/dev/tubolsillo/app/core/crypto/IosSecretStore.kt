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

    override fun savePortalCredentials(institutionKey: String, user: String, password: String) {
        prefs.setObject(user, forKey = "portal_user_" + institutionKey)
        prefs.setObject(password, forKey = "portal_pass_" + institutionKey)
    }

    override fun portalCredentials(institutionKey: String): Pair<String, String>? {
        val u = prefs.stringForKey("portal_user_" + institutionKey) ?: return null
        val p = prefs.stringForKey("portal_pass_" + institutionKey) ?: return null
        return u to p
    }

    override fun clearPortalCredentials(institutionKey: String) {
        prefs.removeObjectForKey("portal_user_" + institutionKey)
        prefs.removeObjectForKey("portal_pass_" + institutionKey)
    }

    override fun savePortalLink(institutionKey: String, link: PortalLink) {
        prefs.setObject(link.movementsUrl, forKey = "portal_url_" + institutionKey)
        prefs.setObject(link.linkedAtMs.toString(), forKey = "portal_linked_" + institutionKey)
        prefs.setObject((link.lastSyncAtMs ?: 0L).toString(), forKey = "portal_sync_" + institutionKey)
    }

    override fun portalLink(institutionKey: String): PortalLink? {
        val url = prefs.stringForKey("portal_url_" + institutionKey) ?: return null
        val linkedAt = prefs.stringForKey("portal_linked_" + institutionKey)?.toLongOrNull() ?: 0L
        val syncAt = prefs.stringForKey("portal_sync_" + institutionKey)?.toLongOrNull() ?: 0L
        return PortalLink(url, linkedAt, syncAt.takeIf { it > 0L })
    }

    override fun clearPortalLink(institutionKey: String) {
        prefs.removeObjectForKey("portal_url_" + institutionKey)
        prefs.removeObjectForKey("portal_linked_" + institutionKey)
        prefs.removeObjectForKey("portal_sync_" + institutionKey)
    }

    private companion object {
        const val KEY_API = "binance_api_key"
        const val KEY_SECRET = "binance_api_secret"
    }
}
