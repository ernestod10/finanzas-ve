package dev.tubolsillo.app.core.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/** Room KMP en iOS: base de datos bajo el directorio de documentos de la app. */
actual fun getDatabaseBuilder(): RoomDatabase.Builder<TubolsilloDatabase> =
    RoomDatabase.Builder<TubolsilloDatabase>(name = "tubolsillo.db")
        .addMigrations(
            TubolsilloDatabase.MIGRATION_1_2,
            TubolsilloDatabase.MIGRATION_2_3,
            TubolsilloDatabase.MIGRATION_3_4,
            TubolsilloDatabase.MIGRATION_4_5,
            TubolsilloDatabase.MIGRATION_5_6,
        )
        .setDriver(BundledSQLiteDriver())
