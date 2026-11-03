package dev.tubolsillo.app.domain.ai

/** Estado del modelo on-device en este dispositivo. */
enum class AnalyzerAvailability {
    CHECKING,
    /** Listo para inferir. */
    AVAILABLE,
    /** El sistema puede descargarlo (AICore). */
    DOWNLOADABLE,
    DOWNLOADING,
    /** Dispositivo o región no soportada. */
    UNAVAILABLE,
}

/** Lectura del modelo sobre un texto crudo. Nunca se escribe sin confirmación. */
data class CaptureReading(
    val amount: Double? = null,
    val currency: String? = null,
    val merchant: String? = null,
    val last4: String? = null,
    val kind: String? = null,
    /** Categoría de gasto sugerida (nombre de categories). */
    val category: String? = null,
    /** false cuando el mensaje es OTP, alerta de saldo o marketing. */
    val isTransaction: Boolean? = null,
    /** Días respecto de hoy para el alta en lenguaje natural (0 = hoy, -1 = ayer). */
    val dayOffset: Int? = null,
    val rawResponse: String? = null,
) {
    val hasData: Boolean get() = amount != null || merchant != null || last4 != null

    /** El modelo dijo explícitamente que no es un movimiento. */
    val isNoise: Boolean get() = isTransaction == false

    val isEmpty: Boolean get() = !hasData && !isNoise
}

/**
 * Segundo lector de capturas y asistente de redacción, siempre on-device.
 *
 * Es **advisory**: la app valida el resultado con sus reglas y el usuario
 * confirma antes de que exista o cambie una transacción.
 */
interface CaptureAnalyzer {
    suspend fun availability(): AnalyzerAvailability

    /** Motivo técnico del último estado, para diagnóstico. */
    suspend fun statusDetail(): String? = null

    /** Descarga el modelo del sistema si hace falta. true si quedó listo. */
    suspend fun ensureModel(onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }): Boolean

    /** Lee una captura. [categories] mejora la sugerencia de categoría. */
    suspend fun analyze(rawText: String, categories: List<String> = emptyList()): CaptureReading?

    /** Interpreta una descripción libre del usuario ("gasté 20 en taxi ayer"). */
    suspend fun interpretEntry(text: String): CaptureReading? = null

    /** Redacción libre con instrucciones acotadas (resúmenes del mes). */
    suspend fun summarize(instructions: String): String? = null

    /**
     * Traduce una pregunta ("¿cuánto gasté en farmacia en septiembre?") a un
     * filtro. El modelo no ve los movimientos: solo produce el filtro.
     */
    suspend fun parseSearch(text: String, categories: List<String>): SearchFilter? = null

    /** Propone qué columna de un CSV desconocido es cada dato. */
    suspend fun mapCsvColumns(header: List<String>, sampleRows: List<String>): CsvColumnMapping? = null

    /** Qué motor responde ahora mismo (para Ajustes). */
    suspend fun engine(): AiEngine = AiEngine.NONE

    /**
     * Categoría sugerida para cada descripción, en el mismo orden. null donde
     * el modelo no encontró una categoría válida.
     */
    suspend fun classifyBatch(descriptions: List<String>, categories: List<String>): List<String?> =
        List(descriptions.size) { null }
}

/** Para plataformas sin modelo on-device (iOS hoy): la cola sigue funcionando a mano. */
class NoopCaptureAnalyzer : CaptureAnalyzer {
    override suspend fun availability(): AnalyzerAvailability = AnalyzerAvailability.UNAVAILABLE
    override suspend fun statusDetail(): String = "Sin lector on-device en esta plataforma."
    override suspend fun ensureModel(onProgress: (Long, Long) -> Unit): Boolean = false
    override suspend fun analyze(rawText: String, categories: List<String>): CaptureReading? = null
    override suspend fun interpretEntry(text: String): CaptureReading? = null
    override suspend fun summarize(instructions: String): String? = null
    override suspend fun parseSearch(text: String, categories: List<String>): SearchFilter? = null
    override suspend fun mapCsvColumns(
        header: List<String>,
        sampleRows: List<String>,
    ): CsvColumnMapping? = null
    override suspend fun classifyBatch(descriptions: List<String>, categories: List<String>): List<String?> =
        List(descriptions.size) { null }
}
