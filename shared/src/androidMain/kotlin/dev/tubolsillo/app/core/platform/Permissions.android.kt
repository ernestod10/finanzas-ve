package dev.tubolsillo.app.core.platform

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

@Composable
actual fun rememberPermissionActions(): PermissionActions {
    val context = LocalContext.current

    fun readState() = PermissionState(
        smsRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED,
        postNotifications = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true,
        notificationListener = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName),
    )

    var state by remember { mutableStateOf(readState()) }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { state = readState() }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { state = readState() }

    val currentState by rememberUpdatedState(state)

    return PermissionActions(
        state = currentState,
        requestSms = { smsLauncher.launch(Manifest.permission.READ_SMS) },
        requestPostNotifications = {
            if (Build.VERSION.SDK_INT >= 33) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        openNotificationAccessSettings = {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            state = readState()
        },
    )
}
