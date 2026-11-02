package dev.tubolsillo.app.domain.ai

/**
 * Modelo de lenguaje que el usuario puede tener en el teléfono cuando el
 * dispositivo no tiene Gemini Nano (AICore).
 *
 * El APK no los incluye: son archivos `.task` (LiteRT/MediaPipe) de cientos de
 * MB. La app recomienda uno según la RAM y el usuario lo importa una vez.
 */
data class LocalModel(
    val id: String,
    val name: String,
    /** Parámetros, para mostrar ("0,5B"). */
    val params: String,
    val sizeMb: Int,
    /** RAM del teléfono a partir de la cual es cómodo. */
    val minRamGb: Int,
    val downloadUrl: String,
    val note: String,
    /** true si la descarga exige cuenta (p. ej. Gemma en Hugging Face). */
    val requiresAccount: Boolean = false,
)

object LocalModelCatalog {

    val QWEN_0_5B = LocalModel(
        id = "qwen2.5-0.5b",
        name = "Qwen 2.5 0.5B",
        params = "0,5B",
        sizeMb = 521,
        minRamGb = 3,
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/" +
            "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        note = "El más liviano. Lee capturas, clasifica y busca. Rápido incluso en gama media.",
    )

    val QWEN_1_5B = LocalModel(
        id = "qwen2.5-1.5b",
        name = "Qwen 2.5 1.5B",
        params = "1,5B",
        sizeMb = 1524,
        minRamGb = 8,
        downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/" +
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        note = "Redacta mejor los resúmenes y entiende frases más largas. Pide 8 GB.",
    )

    val GEMMA_3_1B = LocalModel(
        id = "gemma3-1b",
        name = "Gemma 3 1B",
        params = "1B",
        sizeMb = 554,
        minRamGb = 6,
        downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/" +
            "Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task",
        note = "Buena redacción en español. La descarga pide cuenta de Hugging Face (modelo con acceso).",
        requiresAccount = true,
    )

    val ALL: List<LocalModel> = listOf(QWEN_0_5B, GEMMA_3_1B, QWEN_1_5B)

    fun byId(id: String?): LocalModel? = ALL.firstOrNull { it.id == id }

    /**
     * El más capaz que el teléfono aguanta con holgura. Se deja ~1,5 GB de
     * margen sobre el mínimo: el modelo se carga en RAM además del sistema y
     * de la propia app, y quedarse sin memoria cierra la app del usuario.
     */
    fun recommend(totalRamGb: Double): LocalModel =
        ALL.filter { it.minRamGb <= totalRamGb }.maxByOrNull { it.minRamGb } ?: QWEN_0_5B

    /** Modelos que este teléfono puede correr. */
    fun compatible(totalRamGb: Double): List<LocalModel> = ALL.filter { it.minRamGb <= totalRamGb }
}
