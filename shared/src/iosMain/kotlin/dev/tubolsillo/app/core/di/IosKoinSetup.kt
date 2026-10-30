package dev.tubolsillo.app.core.di

import dev.tubolsillo.app.core.crypto.IosSecretStore
import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.core.notify.CaptureNotifier
import dev.tubolsillo.app.core.notify.IosCaptureNotifier
import dev.tubolsillo.app.core.platform.BackgroundScheduler
import dev.tubolsillo.app.core.platform.IosBackgroundScheduler
import dev.tubolsillo.app.domain.ai.CaptureAnalyzer
import dev.tubolsillo.app.domain.ai.NoopCaptureAnalyzer
import org.koin.core.context.startKoin
import org.koin.dsl.module

/** Implementaciones iOS (secretos, notificaciones, background). */
val iosPlatformModule = module {
    single<SecretStore> { IosSecretStore() }
    single<CaptureNotifier> { IosCaptureNotifier() }
    single<BackgroundScheduler> { IosBackgroundScheduler() }
    // iOS todavía no tiene lector on-device: la cola funciona a mano.
    single<CaptureAnalyzer> { NoopCaptureAnalyzer() }
}

/** Arranca Koin con el grafo completo; lo invoca iOSApp.swift al lanzar. */
fun initKoinIos() {
    startKoin {
        modules(
            commonKoinModules() + domainModule + viewModelModule + iosPlatformModule
        )
    }
}
