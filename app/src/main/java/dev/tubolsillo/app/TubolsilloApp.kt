package dev.tubolsillo.app

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.tubolsillo.app.core.di.androidKoinModules
import dev.tubolsillo.app.core.di.commonKoinModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import javax.inject.Inject

@HiltAndroidApp
class TubolsilloApp : Application(), Configuration.Provider {

    @Inject
    lateinit var configuration: Configuration

    override val workManagerConfiguration: Configuration
        get() = configuration

    override fun onCreate() {
        super.onCreate()
        // Koin alimenta la capa de datos (db/parsers/red); Hilt delega hacia Koin
        // en sus providers (core/di) hasta que M4 elimine Hilt por completo.
        startKoin {
            androidContext(this@TubolsilloApp)
            modules(commonKoinModules() + androidKoinModules())
        }
    }
}
