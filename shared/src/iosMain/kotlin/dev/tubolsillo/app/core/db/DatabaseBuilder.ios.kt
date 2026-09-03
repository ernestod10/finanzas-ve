package dev.tubolsillo.app.core.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/** Room KMP en iOS: base de datos bajo el directorio de documentos de la app. */
actual fun getDatabaseBuilder(): RoomDatabase.Builder<TubolsilloDatabase> =
    RoomDatabase.Builder<TubolsilloDatabase>(name = "tubolsillo.db")
        .setDriver(BundledSQLiteDriver())
