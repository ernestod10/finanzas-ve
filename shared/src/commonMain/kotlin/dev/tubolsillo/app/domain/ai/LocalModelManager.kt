package dev.tubolsillo.app.domain.ai

import kotlinx.coroutines.flow.StateFlow

/** Estado del modelo local en este teléfono, para la pantalla de Ajustes. */
data class LocalModelStatus(
    /** RAM del dispositivo en GB (redondeada). */
    val totalRamGb: Double = 0.0,
    /** El que la app sugiere para esa RAM. */
    val recommended: LocalModel? = null,
    /** Modelo importado por el usuario, si hay. */
    val installedModel: LocalModel? = null,
    /** Tamaño real del archivo importado, en MB. */
    val installedSizeMb: Int = 0,
    /** Qué está leyendo las capturas ahora mismo. */
    val activeEngine: AiEngine = AiEngine.NONE,
) {
    val hasLocalModel: Boolean get() = installedModel != null
}

/** Motor que responde las consultas del modelo. */
enum class AiEngine {
    /** Gemini Nano vía AICore (servicio del sistema). */
    NANO,

    /** Modelo .task importado por el usuario. */
    LOCAL,

    /** Sin IA: la app funciona, pero sin las funciones asistidas. */
    NONE,
}

/**
 * Instala y borra el modelo `.task` que el usuario trae de su cuenta (los
 * archivos pesan cientos de MB, así que no viajan en el APK ni se descargan
 * sin que el usuario lo pida).
 */
interface LocalModelManager {
    val state: StateFlow<LocalModelStatus>

    suspend fun refresh()

    /**
     * Registra un `.task` ya copiado al almacenamiento privado de la app.
     * [modelId] permite saber qué modelo es; si es null se deduce del nombre.
     */
    suspend fun installFromFile(path: String, modelId: String? = null): Result<Unit>

    /** Borra el modelo importado y libera el espacio. */
    suspend fun remove()
}
