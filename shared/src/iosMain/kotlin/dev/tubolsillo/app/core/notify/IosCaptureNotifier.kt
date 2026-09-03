package dev.tubolsillo.app.core.notify

import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.ParsedTransaction
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/** Notificación local en iOS (UNUserNotificationCenter); pide permiso al primer uso. */
class IosCaptureNotifier : CaptureNotifier {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    init {
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { _, _ -> }
    }

    override fun notifyCaptured(parsed: ParsedTransaction, transactionId: Long) {
        val content = UNMutableNotificationContent().apply {
            setTitle(parsed.institution.displayName)
            setBody(
                MoneyFormatter.formatMinor(parsed.amountMinor, parsed.currency) +
                    " en " + parsed.merchant
            )
        }
        val request = UNNotificationRequest.requestWithIdentifier(
            "captura-$transactionId",
            content,
            null,
        )
        center.addNotificationRequest(request) { _ -> }
    }
}
