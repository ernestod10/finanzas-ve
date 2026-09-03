package dev.tubolsillo.app.data.source.binance

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.tubolsillo.app.core.di.sharedKoin

/**
 * Worker de sync de Binance sin Hilt: saca el [BinanceSync] del grafo Koin.
 * El factory se registra en TubolsilloApp vía Configuration.Provider.
 */
class BinanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sync = sharedKoin<BinanceSync>()
        val result = sync.sync()
        return if (result.isSuccess || result.error == "Sin llaves configuradas") {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
