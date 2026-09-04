package dev.tubolsillo.app.data.parse

import dev.tubolsillo.app.core.time.nowMillis

import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.ParsedTransaction


/**
 * Registro de parsers por institución. La detección sin hint usa scoring de
 * keywords por institución ("BanescoInforma", "B/.", "Cashea"…): gana la
 * institución con más coincidencias.
 */
class ParserRegistry(
    parsers: List<InstitutionParser>,
) {

    private val byInstitution: Map<Institution, InstitutionParser> =
        parsers.associateBy { it.institution }

    val institutions: Set<Institution> get() = byInstitution.keys

    fun parserFor(institution: Institution): InstitutionParser? = byInstitution[institution]

    fun parse(
        institution: Institution,
        text: String,
        occurredAt: Long = nowMillis(),
    ): ParsedTransaction? = byInstitution[institution]?.parse(text, occurredAt)

    /** Detecta la institución por keywords del texto, o null. */
    fun detect(text: String): Institution? {
        val lower = text.lowercase()
        val scores = Institution.entries
            .mapNotNull { inst -> keywordsFor(inst)?.let { kw -> inst to kw.count { lower.contains(it) } } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        return scores.firstOrNull()?.first
    }

    private fun keywordsFor(inst: Institution): List<String>? = when (inst) {
        Institution.BANESCO -> listOf("banescoinforma", "banesco")
        Institution.MERCANTIL_PA -> listOf("mercantil panam", "b/.", "mercantilpanama")
        Institution.BANCARIBE -> listOf("bancaribe", "mi conexion bancaribe")
        Institution.BNC -> listOf("bnc ", "banco nacional de credito", "bncenlinea")
        Institution.BDV -> listOf("banco de venezuela", "bdvenlinea", "bdv")
        Institution.CASHEA -> listOf("cashea", "cuota", "quincena")
        Institution.BINANCE -> listOf("binance")
        // El resto se detecta por sender/package o mapeo manual (pantalla de apps).
        else -> null
    }
}
