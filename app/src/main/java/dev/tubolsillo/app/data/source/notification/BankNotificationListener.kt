package dev.tubolsillo.app.data.source.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.tubolsillo.app.core.di.sharedKoin
import dev.tubolsillo.app.core.db.dao.CaptureEventDao
import dev.tubolsillo.app.core.platform.CurrentActivityHolder
import dev.tubolsillo.app.data.parse.ParserRegistry
import dev.tubolsillo.app.domain.ai.CaptureAnalyzer
import dev.tubolsillo.app.domain.model.CaptureOutcome
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.SourceType
import dev.tubolsillo.app.domain.usecase.CapturePipeline
import dev.tubolsillo.app.ui.review.ReviewReadingCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.LinkedHashMap

/**
 * Captura notificaciones push de las apps de los bancos (BDV, BNC, Banesco…),
 * Cashea y clientes de correo (Gmail, Outlook...) cuando contienen notificaciones
 * bancarias (ej. correos transaccionales de Mercantil Panamá).
 * El usuario debe activarlo en Ajustes → Acceso a notificaciones.
 * Filtros baratos ANTES de leer extras: package monitoreado, no ongoing,
 * y LRU de sbn.key (una misma notificación puede postearse varias veces).
 */
class BankNotificationListener : NotificationListenerService() {

    private val pipeline by lazy { sharedKoin<CapturePipeline>() }
    private val registry by lazy { sharedKoin<ParserRegistry>() }
    private val activityHolder by lazy { sharedKoin<CurrentActivityHolder>() }
    private val analyzer by lazy { sharedKoin<CaptureAnalyzer>() }
    private val readingCache by lazy { sharedKoin<ReviewReadingCache>() }
    private val captureEventDao by lazy { sharedKoin<CaptureEventDao>() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** LRU en memoria de keys ya vistas (evita re-procesar el mismo push). */
    private val seen = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
            size > 128
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        val directInstitution = Institution.byNotificationPackage(packageName)
        val isEmailApp = packageName in EMAIL_PACKAGES
        if (directInstitution == null && !isEmailApp) return
        if (sbn.isOngoing) return
        val key = sbn.key ?: return
        synchronized(seen) {
            if (seen.put(key, true) != null) return
        }

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val textLines = extras.getCharSequenceArray("android.textLines")
            ?.mapNotNull { it?.toString() }
            ?.filter { it.isNotBlank() }
            ?.joinToString(" \n ") ?: ""

        val body = listOf(title, text, bigText, textLines)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
        if (body.isBlank()) return

        val institution = directInstitution ?: registry.detect(body) ?: Institution.bySmsSender(title)
        // Si proviene de una app de correo pero no es de ninguna institución bancaria soportada, se descarta.
        if (institution == null && isEmailApp) return

        scope.launch {
            try {
                val outcome = pipeline.ingest(
                    rawText = body,
                    source = if (isEmailApp) SourceType.EMAIL else SourceType.NOTIFICATION,
                    sender = if (isEmailApp) title.ifBlank { packageName } else packageName,
                    hintedInstitution = institution,
                )
                // Oportunista: AICore exige primer plano, así que solo se analiza
                // si la app está visible (el usuario la tiene abierta).
                if (activityHolder.activity != null &&
                    (outcome is CaptureOutcome.Unmatched || outcome is CaptureOutcome.Failed)
                ) {
                    val eventId = captureEventDao.getLatestByRaw(body)?.id ?: return@launch
                    val reading = runCatching { analyzer.analyze(body) }.getOrNull()
                    if (reading != null && !reading.isEmpty) {
                        readingCache.put(eventId, reading)
                    }
                }
            } catch (_: Exception) {
                // El pipeline persiste el evento FAILED.
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No aplica: solo capturamos al postear.
    }

    companion object {
        val EMAIL_PACKAGES = setOf(
            "com.google.android.gm",              // Gmail
            "com.google.android.gm.lite",         // Gmail Go
            "com.microsoft.office.outlook",       // Outlook
            "com.samsung.android.email.provider", // Samsung Email
            "ch.protonmail.android",              // Proton Mail
            "com.fsck.k9",                        // K-9 Mail / Thunderbird
            "com.yahoo.mobile.client.android.mail", // Yahoo Mail
        )
    }
}
