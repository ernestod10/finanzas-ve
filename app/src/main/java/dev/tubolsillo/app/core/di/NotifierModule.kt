package dev.tubolsillo.app.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tubolsillo.app.core.notify.AndroidCaptureNotifier
import dev.tubolsillo.app.core.notify.CaptureNotifier
import javax.inject.Singleton

/** Une la interfaz común del notificador con su implementación Android. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotifierModule {

    @Binds
    @Singleton
    abstract fun bindCaptureNotifier(impl: AndroidCaptureNotifier): CaptureNotifier
}
