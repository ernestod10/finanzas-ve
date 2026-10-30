package dev.tubolsillo.app.portal

import dev.tubolsillo.app.data.source.portal.PortalAccountBalance
import dev.tubolsillo.app.data.source.portal.PortalJs
import dev.tubolsillo.app.data.source.portal.PortalJson
import dev.tubolsillo.app.data.source.portal.PortalRawRow
import dev.tubolsillo.app.data.source.portal.PortalRecipe
import kotlinx.coroutines.delay

/**
 * Sondeo + extracción (con paginación y saldos acumulados) sobre un WebView
 * cualquiera. Lo usan la Activity visible y el runner invisible en segundo plano.
 */
class PortalExtractor(
    private val evalRaw: suspend (String) -> String?,
) {
    var lastSeenBalanceText: String? = null
        private set
    private val lastSeenAccounts = LinkedHashMap<String, PortalAccountBalance>()

    fun hasSeenBalances(): Boolean = lastSeenBalanceText != null || lastSeenAccounts.isNotEmpty()

    suspend fun evaluate(script: String): String? = PortalJs.unwrapResult(evalRaw(script))

    /** login | ready | error_page | unknown. */
    suspend fun probe(recipe: PortalRecipe): String {
        val raw = evaluate(PortalJs.buildProbeScript(recipe)) ?: return "unknown"
        return when {
            raw.contains("\"error_page\"") -> "error_page"
            raw.contains("\"login\"") -> "login"
            raw.contains("\"ready\"") -> "ready"
            else -> "unknown"
        }
    }

    suspend fun probeRaw(recipe: PortalRecipe): String = evaluate(PortalJs.buildProbeScript(recipe)) ?: ""

    private fun mergeSeenAccounts(accounts: List<PortalAccountBalance>) {
        for (acc in accounts) {
            if (acc.amountText.isNotBlank()) {
                val key = "${acc.kind}|${acc.currency}|${acc.mask.ifBlank { acc.label }}"
                lastSeenAccounts[key] = acc
            }
        }
    }

    suspend fun extract(recipe: PortalRecipe, onLog: ((String) -> Unit)? = null): String? {
        val raw = PortalJs.unwrapResult(evalRaw(PortalJs.buildExtractScript(recipe))) ?: return null
        val firstPayload = PortalJson.decode(raw) ?: return raw
        if (firstPayload.balanceText != null) {
            lastSeenBalanceText = firstPayload.balanceText
        }
        mergeSeenAccounts(firstPayload.accounts)
        if (firstPayload.status != "ok" || firstPayload.rows.isEmpty()) {
            val savedBalance = firstPayload.balanceText ?: lastSeenBalanceText
            val savedAccounts = if (lastSeenAccounts.isNotEmpty()) lastSeenAccounts.values.toList() else firstPayload.accounts
            return if (savedBalance != firstPayload.balanceText || savedAccounts != firstPayload.accounts) {
                PortalJson.encode(firstPayload.copy(balanceText = savedBalance, accounts = savedAccounts))
            } else {
                raw
            }
        }

        // Acumular filas de la página 1 y recorrer páginas siguientes si el portal tiene paginación.
        val collected = LinkedHashMap<String, PortalRawRow>()
        fun rowKey(r: PortalRawRow): String =
            "${r.date}|${r.amount}|${r.debit}|${r.credit}|${r.reference}|${r.description}"

        firstPayload.rows.forEach { collected[rowKey(it)] = it }

        var page = 1
        while (page < 12 && collected.size < recipe.maxRows) {
            val nextRes = evaluate(PortalJs.buildNextPageScript())
            android.util.Log.d("PortalWeb", "nextPage($page): $nextRes")
            if (nextRes == null || !nextRes.contains("\"status\":\"next\"")) break
            page += 1
            onLog?.invoke("Leyendo página $page de movimientos (${collected.size} acumulados)…")
            delay(1_250)
            val pageRaw = PortalJs.unwrapResult(evalRaw(PortalJs.buildExtractScript(recipe))) ?: break
            val pagePayload = PortalJson.decode(pageRaw) ?: break
            if (pagePayload.balanceText != null && lastSeenBalanceText == null) {
                lastSeenBalanceText = pagePayload.balanceText
            }
            mergeSeenAccounts(pagePayload.accounts)
            val beforeCount = collected.size
            pagePayload.rows.forEach { row ->
                if (collected.size < recipe.maxRows) collected[rowKey(row)] = row
            }
            // La página siguiente no trajo filas nuevas: llegamos al final.
            if (collected.size == beforeCount) break
        }

        val combined = firstPayload.copy(
            status = "ok",
            balanceText = firstPayload.balanceText ?: lastSeenBalanceText,
            rows = collected.values.toList(),
            accounts = if (lastSeenAccounts.isNotEmpty()) lastSeenAccounts.values.toList() else firstPayload.accounts,
        )
        android.util.Log.d(
            "PortalWeb",
            "Extracted total ${combined.rows.size} rows across $page page(s), balance=${combined.balanceText}, accounts=${combined.accounts.size}"
        )
        return PortalJson.encode(combined)
    }
}
