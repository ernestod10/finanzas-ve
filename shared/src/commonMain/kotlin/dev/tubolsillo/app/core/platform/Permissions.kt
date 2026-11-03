package dev.tubolsillo.app.core.platform

import androidx.compose.runtime.Composable

/** Estado de los permisos relevantes de la plataforma. */
data class PermissionState(
    val postNotifications: Boolean = false,
    val notificationListener: Boolean = false,
)

/** Acciones de permisos disparadas desde la UI (por plataforma). */
data class PermissionActions(
    val state: PermissionState,
    val requestPostNotifications: () -> Unit,
    val openNotificationAccessSettings: () -> Unit,
)

/**
 * Lee y solicita permisos: POST_NOTIFICATIONS y el acceso a notificaciones.
 * (Los SMS se retiraron: la captura va por portales y notificaciones.)
 */
@Composable
expect fun rememberPermissionActions(): PermissionActions
