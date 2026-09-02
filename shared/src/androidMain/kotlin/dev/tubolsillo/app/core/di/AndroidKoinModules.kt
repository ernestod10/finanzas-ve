package dev.tubolsillo.app.core.di

import dev.tubolsillo.app.data.source.binance.BinanceApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.module.Module
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Red hacia Binance (Retrofit hasta M3 que lo reemplaza Ktor).
 * Vive en androidMain mientras siga siendo JVM-only.
 */
val networkModule = module {
    single { Json { ignoreUnknownKeys = true } }
    single {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
    }
    single {
        Retrofit.Builder()
            .baseUrl("https://api.binance.com/")
            .client(get())
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BinanceApi::class.java)
    }
}

fun androidKoinModules(): List<Module> = listOf(networkModule)
