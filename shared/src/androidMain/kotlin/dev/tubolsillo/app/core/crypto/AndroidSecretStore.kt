package dev.tubolsillo.app.core.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Secretos cifrados con Android Keystore (AES256-GCM). */
class AndroidSecretStore(
    private val context: Context,
) : SecretStore {

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "tubolsillo_secrets",
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun saveBinanceKeys(apiKey: String, apiSecret: String) {
        prefs.edit()
            .putString(KEY_BINANCE_API, apiKey.trim())
            .putString(KEY_BINANCE_SECRET, apiSecret.trim())
            .apply()
    }

    override fun binanceKeys(): Pair<String, String>? {
        val key = prefs.getString(KEY_BINANCE_API, null) ?: return null
        val secret = prefs.getString(KEY_BINANCE_SECRET, null) ?: return null
        return key to secret
    }

    override fun hasBinanceKeys(): Boolean = binanceKeys() != null

    override fun clearBinanceKeys() {
        prefs.edit().remove(KEY_BINANCE_API).remove(KEY_BINANCE_SECRET).apply()
    }

    override fun savePortalCredentials(institutionKey: String, user: String, password: String) {
        prefs.edit()
            .putString("portal_user_" + institutionKey, user)
            .putString("portal_pass_" + institutionKey, password)
            .apply()
    }

    override fun portalCredentials(institutionKey: String): Pair<String, String>? {
        val u = prefs.getString("portal_user_" + institutionKey, null) ?: return null
        val p = prefs.getString("portal_pass_" + institutionKey, null) ?: return null
        return u to p
    }

    override fun clearPortalCredentials(institutionKey: String) {
        prefs.edit()
            .remove("portal_user_" + institutionKey)
            .remove("portal_pass_" + institutionKey)
            .apply()
    }

    override fun savePortalLink(institutionKey: String, link: PortalLink) {
        prefs.edit()
            .putString("portal_url_" + institutionKey, link.movementsUrl)
            .putLong("portal_linked_" + institutionKey, link.linkedAtMs)
            .putLong("portal_sync_" + institutionKey, link.lastSyncAtMs ?: 0L)
            .remove("portal_cookies_" + institutionKey)
            .apply()
    }

    override fun portalLink(institutionKey: String): PortalLink? {
        val url = prefs.getString("portal_url_" + institutionKey, null) ?: return null
        val linkedAt = prefs.getLong("portal_linked_" + institutionKey, 0L)
        val syncAt = prefs.getLong("portal_sync_" + institutionKey, 0L)
        return PortalLink(url, linkedAt, syncAt.takeIf { it > 0L })
    }

    override fun clearPortalLink(institutionKey: String) {
        prefs.edit()
            .remove("portal_url_" + institutionKey)
            .remove("portal_linked_" + institutionKey)
            .remove("portal_sync_" + institutionKey)
            .remove("portal_cookies_" + institutionKey)
            .apply()
    }

    private companion object {
        const val KEY_BINANCE_API = "binance_api_key"
        const val KEY_BINANCE_SECRET = "binance_api_secret"
    }
}
