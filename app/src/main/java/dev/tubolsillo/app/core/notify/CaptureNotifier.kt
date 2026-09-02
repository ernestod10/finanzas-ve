package dev.tubolsillo.app.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tubolsillo.app.MainActivity
import dev.tubolsillo.app.R
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.ParsedTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notificación local de captura: "Banesco: −Bs. 25,00 en FARMACIA X".
 * Canal silencioso "Capturas"; en Android 13+ requiere POST_NOTIFICATIONS.
 */
@Singleton
class CaptureNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun notifyCaptured(parsed: ParsedTransaction, transactionId: Long) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Capturas", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Transacciones capturadas automáticamente"
                enableVibration(false)
            }
        )

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            transactionId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(parsed.institution.displayName)
            .setContentText(
                MoneyFormatter.formatMinor(parsed.amountMinor, parsed.currency) +
                    " en " + parsed.merchant
            )
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(transactionId.toInt(), notification)
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones: no hay nada que hacer.
        }
    }

    private companion object {
        const val CHANNEL_ID = "capturas"
    }
}
