package dev.tubolsillo.app.data.source.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.tubolsillo.app.core.di.sharedKoin
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.SourceType
import dev.tubolsillo.app.domain.usecase.CapturePipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.LinkedHashMap

/**
 * Captura notificaciones push de las apps de los bancos (BDV, BNC, Banesco…)
 * y Cashea. El usuario debe activarlo en Ajustes → Acceso a notificaciones.
 * Filtros baratos ANTES de leer extras: package monitoreado, no ongoing,
 * y LRU de sbn.key (una misma notificación puede postearse varias veces).
 */
class BankNotificationListener : NotificationListenerService() {

    private val pipeline by lazy { sharedKoin<CapturePipeline>() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** LRU en memoria de keys ya vistas (evita re-procesar el mismo push). */
    private val seen = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
            size > 128
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        if (Institution.byNotificationPackage(packageName) == null) return
        if (sbn.isOngoing) return
        val key = sbn.key ?: return
        synchronized(seen) {
            if (seen.put(key, true) != null) return
        }

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val body = listOf(title, text, bigText)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        if (body.isBlank()) return

        val institution = Institution.byNotificationPackage(packageName)
        scope.launch {
            try {
                pipeline.ingest(
                    rawText = body,
                    source = SourceType.NOTIFICATION,
                    sender = packageName,
                    hintedInstitution = institution,
                )
            } catch (_: Exception) {
                // El pipeline persiste el evento FAILED.
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No aplica: solo capturamos al postear.
    }
}
