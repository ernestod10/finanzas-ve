package dev.tubolsillo.app.core.platform

/**
 * iOS: BGTaskScheduler se registra en M6 junto con el conector IMAP.
 * Por ahora "syncNow" es no-op (el usuario puede sincronizar desde la app).
 */
class IosBackgroundScheduler : BackgroundScheduler {
    override fun schedulePeriodicSync() {
        // TODO(M6): BGAppRefreshTaskRequest dev.tubolsillo.mailfetch
    }

    override fun syncNow() {
        // TODO(M6): ejecutar EmailImporter.sync() en background task
    }
}
