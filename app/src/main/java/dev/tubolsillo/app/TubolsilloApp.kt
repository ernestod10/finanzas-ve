package dev.tubolsillo.app

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkerFactory
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import dev.tubolsillo.app.core.di.androidPlatformModule
import dev.tubolsillo.app.core.di.commonKoinModules
import dev.tubolsillo.app.core.di.domainModule
import dev.tubolsillo.app.core.di.viewModelModule
import dev.tubolsillo.app.data.source.binance.BinanceWorker
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TubolsilloApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(BinanceWorkerFactory())
            .build()

    override fun onCreate() {
        super.onCreate()
        // Un solo grafo Koin: dominio+UI en shared, plataforma Android aquí.
        startKoin {
            androidContext(this@TubolsilloApp)
            modules(
                commonKoinModules() + domainModule + viewModelModule + androidPlatformModule
            )
        }
        // El portal se lanza desde la Activity en primer plano, no desde el
        // contexto de aplicación: así vuelve a la pantalla de donde salió.
        registerActivityLifecycleCallbacks(
            org.koin.core.context.GlobalContext.get()
                .get<dev.tubolsillo.app.core.platform.CurrentActivityHolder>()
        )
    }
}

/** Crea el BinanceWorker inyectando el grafo Koin (sin Hilt ni koin-android-work). */
class BinanceWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        BinanceWorker::class.java.name -> BinanceWorker(appContext, workerParameters)
        else -> null
    }
}
