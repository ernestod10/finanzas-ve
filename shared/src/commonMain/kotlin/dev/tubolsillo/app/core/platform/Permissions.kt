package dev.tubolsillo.app.core.platform

import androidx.compose.runtime.Composable

/** Estado de los permisos relevantes de la plataforma. */
data class PermissionState(
    val smsRead: Boolean = false,
    val postNotifications: Boolean = false,
    val notificationListener: Boolean = false,
)

/** Acciones de permisos disparadas desde la UI (por plataforma). */
data class PermissionActions(
    val state: PermissionState,
    val requestSms: () -> Unit,
    val requestPostNotifications: () -> Unit,
    val openNotificationAccessSettings: () -> Unit,
)

/**
 * Lee y solicita permisos. Android: READ_SMS, POST_NOTIFICATIONS y la pantalla
 * de acceso a notificaciones. iOS (M5): no-op para SMS (imposible en Apple),
 * las notificaciones se piden vía UNUserNotificationCenter.
 */
@Composable
expect fun rememberPermissionActions(): PermissionActions
