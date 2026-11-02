package dev.tubolsillo.app.ai

import android.app.ActivityManager
import android.content.Context
import dev.tubolsillo.app.domain.ai.LocalModelCatalog
import dev.tubolsillo.app.domain.ai.LocalModelManager
import dev.tubolsillo.app.domain.ai.LocalModelStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Guarda el modelo `.task` importado por el usuario en el almacenamiento
 * privado de la app y recuerda cuál es. La RAM del teléfono decide qué modelo
 * se recomienda.
 */
class AndroidLocalModelManager(
    private val context: Context,
) : LocalModelManager {

    private val prefs = context.getSharedPreferences("local_model", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readState())
    override val state: StateFlow<LocalModelStatus> = _state.asStateFlow()

    /** Carpeta donde el picker deja los `.task`. */
    fun modelsDir(): File = File(context.filesDir, "models").apply { mkdirs() }

    /** El archivo importado, si sigue existiendo. */
    fun modelFile(): File? = prefs.getString(KEY_PATH, null)
        ?.let(::File)
        ?.takeIf { it.isFile && it.length() > MIN_BYTES }

    override suspend fun refresh() {
        _state.value = readState()
    }

    override suspend fun installFromFile(path: String, modelId: String?): Result<Unit> = runCatching {
        val file = File(path)
        require(file.isFile) { "El archivo no existe" }
        require(file.length() > MIN_BYTES) { "El archivo no parece un modelo (menos de 50 MB)" }
        val id = modelId ?: guessModelId(file.name)
        prefs.edit()
            .putString(KEY_PATH, file.absolutePath)
            .putString(KEY_ID, id)
            .apply()
        _state.value = readState()
    }

    override suspend fun remove() {
        prefs.edit().remove(KEY_PATH).remove(KEY_ID).apply()
        modelsDir().listFiles()?.forEach { it.delete() }
        _state.value = readState()
    }

    private fun readState(): LocalModelStatus {
        val ram = totalRamGb()
        val id = prefs.getString(KEY_ID, null)
        val file = modelFile()
        val model = when {
            file == null -> null
            else -> LocalModelCatalog.byId(id)
                ?: LocalModelCatalog.ALL.firstOrNull { file.name.contains(it.id, ignoreCase = true) }
                ?: LocalModelCatalog.QWEN_0_5B
        }
        return LocalModelStatus(
            totalRamGb = ((ram * 10).toInt()) / 10.0,
            recommended = LocalModelCatalog.recommend(ram),
            installedModel = model,
            installedSizeMb = (((file?.length() ?: 0L) / (1024 * 1024)).toInt()),
        )
    }

    private fun totalRamGb(): Double {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 4.0
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return info.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
    }

    private fun guessModelId(fileName: String): String =
        LocalModelCatalog.ALL.firstOrNull { fileName.contains(it.id, ignoreCase = true) }?.id
            ?: when {
                fileName.contains("gemma", ignoreCase = true) -> LocalModelCatalog.GEMMA_3_1B.id
                fileName.contains("1.5", ignoreCase = true) -> LocalModelCatalog.QWEN_1_5B.id
                else -> LocalModelCatalog.QWEN_0_5B.id
            }

    /** Copia el `.task` elegido en el selector de archivos a la carpeta privada. */
    fun copyIntoModels(open: () -> java.io.InputStream?, name: String?): File? {
        val stream = open() ?: return null
        val safeName = (name ?: "modelo.task").substringAfterLast('/').ifBlank { "modelo.task" }
        val target = File(modelsDir(), if (safeName.endsWith(".task", true)) safeName else "$safeName.task")
        stream.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target.takeIf { it.length() > MIN_BYTES }
    }

    companion object {
        private const val KEY_PATH = "model_path"
        private const val KEY_ID = "model_id"

        /** Por debajo de esto no es un modelo (evita registrar un archivo equivocado). */
        private const val MIN_BYTES = 50L * 1024 * 1024
    }
}
