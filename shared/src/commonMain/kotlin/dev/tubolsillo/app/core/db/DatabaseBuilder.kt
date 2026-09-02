package dev.tubolsillo.app.core.db

import androidx.room.RoomDatabase

/**
 * Builder de la base de datos por plataforma (Room KMP).
 * El nombre de archivo "tubolsillo.db" NO cambia: las instalaciones
 * existentes conservan su data al migrar a shared.
 */
expect fun getDatabaseBuilder(): RoomDatabase.Builder<TubolsilloDatabase>
