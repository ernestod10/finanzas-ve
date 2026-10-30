package dev.tubolsillo.app.core.di

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.tubolsillo.app.ai.NanoCaptureAnalyzer
import dev.tubolsillo.app.core.crypto.AndroidSecretStore
import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.core.notify.AndroidCaptureNotifier
import dev.tubolsillo.app.core.notify.CaptureNotifier
import dev.tubolsillo.app.core.platform.BackgroundScheduler
import dev.tubolsillo.app.core.platform.CurrentActivityHolder
import dev.tubolsillo.app.core.platform.PortalGateway
import dev.tubolsillo.app.domain.ai.CaptureAnalyzer
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
    // Gemini Nano vía AICore; si el dispositivo no lo soporta, la cola sigue a mano.
    single<CaptureAnalyzer> { NanoCaptureAnalyzer() }
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
    }
}
