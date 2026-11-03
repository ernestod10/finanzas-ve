package dev.tubolsillo.app.data.source.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.tubolsillo.app.core.db.dao.AccountDao
import dev.tubolsillo.app.core.db.dao.InstallmentDao
import dev.tubolsillo.app.core.di.sharedKoin
import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.domain.reminders.DueAlert
import dev.tubolsillo.app.domain.reminders.DueReminders
import kotlinx.coroutines.flow.first

/**
 * Aviso diario de pagos que se acercan: vencimiento de tarjetas y cuotas de
 * Cashea. Corre una vez al día con WorkManager y no repite el mismo aviso.
 */
class DueReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val accountDao = sharedKoin<AccountDao>()
        val installmentDao = sharedKoin<InstallmentDao>()

        val activeAccounts = accountDao.observeActive().first()
        val pendingInstallments = installmentDao.observePending().first()
        val alerts = DueReminders.compute(activeAccounts, pendingInstallments, nowMillis())
        if (alerts.isEmpty()) return Result.success()

        // Se recuerda lo ya avisado SOLO para el día de hoy: al cambiar el día
        // todo se re-arma y el aviso vuelve a corresponder.
        val prefs = applicationContext.getSharedPreferences("due_reminders", Context.MODE_PRIVATE)
        val today = nowMillis() / DAY_MS
        val storedDay = prefs.getLong(KEY_DAY, -1L)
        val alreadyNotified = if (storedDay == today) {
            prefs.getStringSet(KEY_KEYS, emptySet()).orEmpty()
        } else {
            emptySet()
        }

        val newlyPosted = alerts
            .filter { alert -> alert.key !in alreadyNotified && post(alert) }
            .map { it.key }

        prefs.edit()
            .putLong(KEY_DAY, today)
            .putStringSet(KEY_KEYS, alreadyNotified + newlyPosted)
            .apply()
        return Result.success()
    }

    private fun post(alert: DueAlert): Boolean {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Vencimientos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Pagos de tarjetas y cuotas que están por vencer"
            }
        )

        val intent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP) ?: return false
        val pending = PendingIntent.getActivity(
            applicationContext,
            alert.key.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setPriority(if (alert.daysLeft == 0) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        return try {
            NotificationManagerCompat.from(applicationContext).notify(alert.key.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private companion object {
        const val CHANNEL_ID = "vencimientos"
        const val DAY_MS = 86_400_000L
        const val KEY_DAY = "day"
        const val KEY_KEYS = "keys"
    }
}
