package dev.tubolsillo.app.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Registro crudo append-only de cada captura (SMS, push, etc.) con su veredicto.
 * Es la mina de oro del tuning de parsers y la red de seguridad ante deriva
 * de formatos de los bancos: el raw nunca se pierde y se puede re-parsear.
 */
@Entity(
    tableName = "capture_events",
    indices = [Index("capturedAt")],
)
data class CaptureEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val capturedAt: Long,
    val institution: String? = null,
    val rawText: String,
    val sender: String? = null,
    val parseStatus: String,
    val parsedTransactionId: Long? = null,
    val parserVersion: Int? = null,
    val errorMessage: String? = null,
)
