package dev.tubolsillo.app.core.notify

import dev.tubolsillo.app.domain.model.ParsedTransaction

/**
 * Notificación local de captura: "Banesco: −Bs. 25,00 en FARMACIA X".
 * Interfaz común; cada plataforma provee su implementación
 * (Android: NotificationManager; iOS: UNUserNotificationCenter en M5).
 */
interface CaptureNotifier {
    fun notifyCaptured(parsed: ParsedTransaction, transactionId: Long)
}
