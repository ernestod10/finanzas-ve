package dev.tubolsillo.app.core.platform

import androidx.compose.runtime.Composable

/**
 * iOS no permite leer SMS ni interceptar notificaciones de terceros: esas
 * filas no aplican. Las notificaciones locales se piden al primer uso vía
 * UNUserNotificationCenter (IosCaptureNotifier). Estado informativo.
 */
@Composable
actual fun rememberPermissionActions(): PermissionActions = PermissionActions(
    state = PermissionState(
        postNotifications = true,
        notificationListener = true,
    ),
    requestPostNotifications = {},
    openNotificationAccessSettings = {},
)
