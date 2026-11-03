package dev.tubolsillo.app.core.di

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.tubolsillo.app.ai.AndroidLocalModelManager
import dev.tubolsillo.app.ai.ChainedCaptureAnalyzer
import dev.tubolsillo.app.ai.LocalLlmAnalyzer
import dev.tubolsillo.app.ai.NanoCaptureAnalyzer
import dev.tubolsillo.app.core.crypto.AndroidSecretStore
import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.core.notify.AndroidCaptureNotifier
import dev.tubolsillo.app.core.notify.CaptureNotifier
import dev.tubolsillo.app.core.platform.BackgroundScheduler
import dev.tubolsillo.app.core.platform.CurrentActivityHolder
import dev.tubolsillo.app.core.platform.PortalGateway
import dev.tubolsillo.app.domain.ai.CaptureAnalyzer
import dev.tubolsillo.app.domain.ai.LocalModelManager
import dev.tubolsillo.app.portal.AndroidPortalGateway
import dev.tubolsillo.app.data.source.binance.BinanceSync
import dev.tubolsillo.app.data.source.binance.BinanceWorker
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Implementaciones de plataforma Android registradas en Koin:
 * secretos cifrados (Keystore), notificador de capturas y sincronización en
 * segundo plano (WorkManager). Vive en :app porque usa APIs Android-only.
 */
val androidPlatformModule = module {
    single<SecretStore> { AndroidSecretStore(androidContext()) }
    single<CaptureNotifier> { AndroidCaptureNotifier(androidContext()) }
    single<BackgroundScheduler> { WorkManagerScheduler(androidContext()) }
    single { CurrentActivityHolder() }
    single<PortalGateway> { AndroidPortalGateway(androidContext(), get()) }
    // Lector de capturas: Gemini Nano (AICore) si el teléfono lo soporta; si no,
    // el modelo .task que el usuario haya importado. Sin ninguno, la app sigue
    // funcionando y la cola de revisión se resuelve a mano.
    single { AndroidLocalModelManager(androidContext()) }
    single<LocalModelManager> { get<AndroidLocalModelManager>() }
    single<CaptureAnalyzer> {
        ChainedCaptureAnalyzer(
            nano = NanoCaptureAnalyzer(),
            local = LocalLlmAnalyzer(androidContext(), get()),
        )
    }
}

/** Programación en segundo plano con WorkManager (sync de Binance). */
class WorkManagerScheduler(private val context: android.content.Context) : BackgroundScheduler {

    override fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<BinanceWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun scheduleDailyReminders() {
        val request = PeriodicWorkRequestBuilder<dev.tubolsillo.app.data.source.reminders.DueReminderWorker>(
            1, TimeUnit.DAYS,
        ).setInitialDelay(hoursUntilNine(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_REMINDERS,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Primer aviso a las 9:00 locales, no a la hora en que se abrió la app. */
    private fun hoursUntilNine(): Long {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis - System.currentTimeMillis()
    }

    override fun syncNow() {
        val request = OneTimeWorkRequestBuilder<BinanceWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NOW,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private companion object {
        const val UNIQUE_PERIODIC = "binance_sync_periodic"
        const val UNIQUE_NOW = "binance_sync_now"
        const val UNIQUE_REMINDERS = "due_reminders_daily"
    }
}
