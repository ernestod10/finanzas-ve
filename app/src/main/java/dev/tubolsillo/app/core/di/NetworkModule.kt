package dev.tubolsillo.app.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tubolsillo.app.data.source.binance.BinanceApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.java.KoinJavaComponent.getKoin
import javax.inject.Singleton

/** Puente temporal Hilt→Koin para la red de Binance (M3 cambia a Ktor). */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = getKoin().get()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = getKoin().get()

    @Provides
    @Singleton
    fun provideBinanceApi(): BinanceApi = getKoin().get()
}
