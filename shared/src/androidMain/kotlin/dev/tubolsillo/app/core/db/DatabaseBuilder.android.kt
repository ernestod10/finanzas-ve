package dev.tubolsillo.app.core.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.koin.core.context.GlobalContext

actual fun getDatabaseBuilder(): RoomDatabase.Builder<TubolsilloDatabase> =
    Room.databaseBuilder(
        GlobalContext.get().get<android.content.Context>(),
        TubolsilloDatabase::class.java,
        "tubolsillo.db",
    ).addMigrations(TubolsilloDatabase.MIGRATION_1_2)
        .setDriver(BundledSQLiteDriver())
