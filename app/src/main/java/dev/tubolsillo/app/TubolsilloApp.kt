package dev.tubolsillo.app

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TubolsilloApp : Application(), Configuration.Provider {

    @Inject
    lateinit var configuration: Configuration

    override val workManagerConfiguration: Configuration
        get() = configuration
}
