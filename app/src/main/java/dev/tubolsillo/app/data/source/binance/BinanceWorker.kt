package dev.tubolsillo.app.data.source.binance

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Sync periódico de Binance (mínimo 15 min, con red y batería no baja).
 * El resultado no importa mucho: la sync es idempotente (dedup por id de
 * depósito/retiro y snapshots que solo pisan el saldo).
 */
@HiltWorker
class BinanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: BinanceSync,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = sync.sync()
        return if (result.isSuccess || result.error == "Sin llaves configuradas") {
            Result.success()
        } else {
            // Errores de red/API: reintentar con backoff estándar.
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC = "binance_sync_periodic"
        private const val UNIQUE_NOW = "binance_sync_now"

        fun schedulePeriodic(context: Context) {
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

        fun syncNow(context: Context) {
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
    }
}
