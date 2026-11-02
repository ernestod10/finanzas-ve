package dev.tubolsillo.app.domain.assistant

import dev.tubolsillo.app.domain.ai.AiResponseParser
import dev.tubolsillo.app.domain.ai.aiBoolean
import dev.tubolsillo.app.domain.ai.aiDouble
import dev.tubolsillo.app.domain.ai.aiInt
import dev.tubolsillo.app.domain.ai.aiString

/**
 * Lo que el usuario le pidió al asistente, ya traducido del lenguaje natural.
 *
 * El modelo **solo produce esta intención**; los números los calcula la app
 * sobre la base de datos, así que ninguna cifra puede salir inventada.
 */
sealed interface AssistantIntent {
    /** Cuánto se gastó/ingresó en un período, opcionalmente por categoría o texto. */
    data class Stats(
        val monthsBack: Int = 0,
        val category: String? = null,
        val text: String? = null,
    ) : AssistantIntent

    /** "El gasto de 20 de ayer fue un almuerzo": buscar el movimiento. */
    data class Find(
        val text: String? = null,
        val amount: Double? = null,
        val daysAgo: Int? = null,
    ) : AssistantIntent

    /** Igual que Find, pero además corrigiendo descripción y categoría. */
    data class Update(
        val text: String? = null,
        val amount: Double? = null,
        val daysAgo: Int? = null,
        val description: String? = null,
        val category: String? = null,
    ) : AssistantIntent

    /**
     * "360 en divisas o 500 a BCV: ¿cuál me sale mejor?"
     *
     * Puede venir un solo precio (entonces se compara contra la tasa BCV) o
     * los dos, que es el caso típico: el comercio cobra X en dólares o Y en
     * bolívares.
     */
    data class Compare(
        /** Precio pagando en dólares en efectivo. */
        val usdPrice: Double? = null,
        /** Precio en dólares que se pagan en bolívares a la tasa BCV ("580 a BCV"). */
        val bcvUsdPrice: Double? = null,
        /** Precio ya expresado en bolívares. */
        val bsPrice: Double? = null,
        /** Descuento sobre la opción en divisas, si lo mencionan. */
        val discountPct: Double? = null,
    ) : AssistantIntent

    data object Help : AssistantIntent
}

/**
 * Lee la intención del JSON del modelo. Si no hay JSON usable devuelve
 * [AssistantIntent.Help]: nunca se cae la conversación.
 */
object AssistantIntentParser {

    fun parse(response: String?, categories: List<String> = emptyList()): AssistantIntent {
        val obj = AiResponseParser.jsonObject(response) ?: return heuristic(response.orEmpty())
        return when (val action = obj.aiString("action")?.lowercase()) {
            "stats" -> AssistantIntent.Stats(
                monthsBack = (obj.aiInt("months_back") ?: 0).coerceIn(0, 24),
                category = validCategory(obj.aiString("category"), categories),
                text = obj.aiString("text"),
            )

            "find" -> AssistantIntent.Find(
                text = obj.aiString("text"),
                amount = obj.aiDouble("amount"),
                daysAgo = obj.aiInt("days_ago"),
            )

            "update" -> AssistantIntent.Update(
                text = obj.aiString("text"),
                amount = obj.aiDouble("amount"),
                daysAgo = obj.aiInt("days_ago"),
                description = obj.aiString("description"),
                category = validCategory(obj.aiString("category"), categories),
            )

            "compare" -> {
                val usd = obj.aiDouble("price_usd") ?: obj.aiDouble("usd")
                val bcvUsd = obj.aiDouble("price_bcv_usd") ?: obj.aiDouble("bcv_usd")
                val bs = obj.aiDouble("price_bs") ?: obj.aiDouble("bs")
                // Compatibilidad: "price" con currency.
                val legacy = obj.aiDouble("price")
                val legacyCurrency = obj.aiString("currency")?.uppercase()
                val usdPrice = usd ?: legacy?.takeIf { legacyCurrency != "VES" }
                val bsPrice = bs ?: legacy?.takeIf { legacyCurrency == "VES" }
                val bcvUsdPrice = bcvUsd?.takeIf { it > 0 }
                val hasAny = listOfNotNull(usdPrice, bcvUsdPrice, bsPrice).any { it > 0 }
                if (!hasAny) {
                    AssistantIntent.Help
                } else {
                    AssistantIntent.Compare(
                        usdPrice = usdPrice?.takeIf { it > 0 },
                        bcvUsdPrice = bcvUsdPrice,
                        bsPrice = bsPrice?.takeIf { it > 0 },
                        discountPct = obj.aiDouble("discount_pct"),
                    )
                }
            }

            else -> heuristic(response.orEmpty())
        }
    }

    private fun validCategory(name: String?, categories: List<String>): String? {
        if (name.isNullOrBlank() || categories.isEmpty()) return name?.takeIf { it.isNotBlank() }
        return categories.firstOrNull { it.equals(name, ignoreCase = true) }
    }

    /**
     * Sin modelo (o si respondió cualquier cosa): reglas mínimas para que el
     * chat siga sirviendo en "¿cuánto gasté este mes?" y en la comparación de
     * tasas, que son las dos preguntas más frecuentes.
     */
    fun heuristic(text: String): AssistantIntent {
        val lower = text.lowercase()
        val amount = Regex("\\d+(?:[.,]\\d+)?").find(text)?.value
            ?.replace(".", "")
            ?.replace(',', '.')
            ?.toDoubleOrNull()

        if (lower.contains("divisa") || lower.contains("bcv") || lower.contains("binance")) {
            val numbers = Regex("\\d+(?:[.,]\\d+)?").findAll(text)
                .mapNotNull { m ->
                    m.value.replace(".", "").replace(',', '.').toDoubleOrNull()
                }
                .filter { it > 0 }
                .toList()
            if (numbers.isNotEmpty()) {
                val discount = Regex("(\\d{1,2})\\s*%").find(text)?.groupValues?.get(1)?.toIntOrNull()
                val discountValue = discount?.toDouble()?.takeIf { it > 0 }
                val explicitUsd = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:en\\s+)?divisas", RegexOption.IGNORE_CASE)
                    .find(text)?.groupValues?.get(1)
                    ?.replace(".", "")?.replace(',', '.')?.toDoubleOrNull()
                // "580 a tasa BCV" se dice en Venezuela para un precio en DÓLARES
                // que se paga en bolívares al cambio oficial.
                val explicitBcvUsd = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:usd|d[oó]lares|\\$)?\\s*(?:a|en|por)\\s*(?:la\\s+)?(?:tasa\\s+)?bcv", RegexOption.IGNORE_CASE)
                    .find(text)?.groupValues?.get(1)
                    ?.replace(".", "")?.replace(',', '.')?.toDoubleOrNull()
                val explicitBs = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:bs|bol[ií]vares)", RegexOption.IGNORE_CASE)
                    .find(text)?.groupValues?.get(1)
                    ?.replace(".", "")?.replace(',', '.')?.toDoubleOrNull()
                val withoutDiscount = numbers.filter { it != discountValue }
                return AssistantIntent.Compare(
                    usdPrice = explicitUsd ?: withoutDiscount.firstOrNull() ?: numbers.firstOrNull(),
                    bcvUsdPrice = explicitBcvUsd,
                    bsPrice = explicitBs,
                    discountPct = discountValue,
                )
            }
        }
        if (lower.contains("gast") || lower.contains("cuánto") || lower.contains("cuanto") ||
            lower.contains("ingres")
        ) {
            return AssistantIntent.Stats()
        }
        return AssistantIntent.Help
    }
}
