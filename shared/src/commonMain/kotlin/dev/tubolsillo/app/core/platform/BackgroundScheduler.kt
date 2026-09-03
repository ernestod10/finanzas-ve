package dev.tubolsillo.app.core.platform

/**
 * Sincronización en segundo plano por plataforma.
 * Android: WorkManager (sync de Binance periódica). iOS (M5): BGTaskScheduler.
 * Los conectores la implementan y registran en el módulo Koin de plataforma.
 */
interface BackgroundScheduler {
    fun schedulePeriodicSync()
    fun syncNow()
}
