package dev.tubolsillo.app.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tubolsillo.app.data.source.binance.BinanceApi
import org.koin.java.KoinJavaComponent.getKoin
import javax.inject.Singleton

/** Puente temporal Hilt→Koin para la red de Binance (M4 lo elimina). */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideBinanceApi(): BinanceApi = getKoin().get()
}
