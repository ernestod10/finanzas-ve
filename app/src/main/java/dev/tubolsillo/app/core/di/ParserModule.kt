package dev.tubolsillo.app.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tubolsillo.app.data.parse.ParserRegistry
import org.koin.java.KoinJavaComponent.getKoin

/** Puente temporal Hilt→Koin para el registro de parsers (M4 lo elimina). */
@Module
@InstallIn(SingletonComponent::class)
object ParserModule {

    @Provides
    fun provideParserRegistry(): ParserRegistry = getKoin().get()
}
