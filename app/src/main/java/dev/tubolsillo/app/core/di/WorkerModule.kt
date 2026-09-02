package dev.tubolsillo.app.core.di

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * WorkManager + Hilt: el constructor de HiltWorkerFactory no es público, así
 * que se obtiene vía EntryPoint (patrón oficial de androidx.hilt 1.x).
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {

    @Provides
    @Singleton
    fun provideWorkManagerConfiguration(@ApplicationContext context: Context): Configuration {
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            HiltWorkerFactoryEntryPoint::class.java,
        )
        return Configuration.Builder()
            .setWorkerFactory(entryPoint.workerFactory())
            .build()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HiltWorkerFactoryEntryPoint {
        fun workerFactory(): HiltWorkerFactory
    }
}
