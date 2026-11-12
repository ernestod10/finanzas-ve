package dev.tubolsillo.app.data.source.portal

import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.core.platform.PortalFetchResult
import dev.tubolsillo.app.core.platform.PortalGateway
import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.repo.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Consulta automática al abrir la app: recorre, uno por uno, los bancos
 * vinculados que tienen credenciales guardadas (para poder auto-loguearse) y
 * cuya última consulta tiene más de [MIN_INTERVAL_MS]. Se ejecuta una sola vez
 * por proceso; si un banco pide 2FA o falla, se sigue con el siguiente.
 */
class PortalAutoSync(
    private val secretStore: SecretStore,
    private val gateway: PortalGateway,
    private val importer: PortalImporter,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()
    private var ranThisProcess = false

    suspend fun runOnAppOpen() {
        if (ranThisProcess) return
        mutex.withLock {
            if (ranThisProcess) return
            ranThisProcess = true
            if (!settings.autoSyncPortalsOnOpen()) return
            // Dar tiempo a que MainActivity quede registrada en primer plano.
            delay(1_500)
            for (institution in candidates()) {
                runCatching { syncOne(institution) }
            }
        }
    }

    /**
     * Consulta manual (arrastrar hacia abajo en Inicio): no espera el intervalo
     * mínimo ni respeta el "una vez por proceso", porque el usuario lo pidió.
     * Sigue sin tocar portales sin credenciales guardadas.
     */
    suspend fun refreshNow() {
        mutex.withLock {
            for (institution in candidates(ignoreInterval = true)) {
                runCatching { syncOne(institution) }
            }
        }
    }

    private fun candidates(ignoreInterval: Boolean = false): List<Institution> {
        val now = nowMillis()
        return Institution.entries.filter { inst ->
            if (inst.portalStartUrl == null || inst == Institution.OTHER) return@filter false
            val link = secretStore.portalLink(inst.key) ?: return@filter false
            if (secretStore.portalCredentials(inst.key) == null) return@filter false
            if (PortalRecipes.forInstitution(inst) == null) return@filter false
            if (ignoreInterval) return@filter true
            val last = link.lastSyncAtMs ?: 0L
            now - last >= MIN_INTERVAL_MS
        }
    }

    private suspend fun syncOne(institution: Institution) {
        val link = secretStore.portalLink(institution.key) ?: return
        val recipe = PortalRecipes.forInstitution(institution) ?: return
        when (val result = gateway.fetch(institution.key, link.movementsUrl)) {
            is PortalFetchResult.Success -> {
                val now = nowMillis()
                val mapped = PortalRowMapper.map(result.payload, recipe, now)
                importer.import(recipe, mapped)
                secretStore.savePortalLink(institution.key, link.copy(lastSyncAtMs = now))
            }
            else -> Unit
        }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 10 * 60 * 1000L
    }
}
