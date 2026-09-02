package dev.tubolsillo.app.data.parse

import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.ParsedTransaction

/**
 * Parser de una institución: texto crudo de SMS/push → ParsedTransaction.
 * Es canal-agnóstico (el mismo parser sirve SMS hoy, email mañana) y
 * versionado: al afinar reglas se sube [version] y los CaptureEvents viejos
 * guardan su parserVersion para poder re-parsearlos con las reglas nuevas.
 * Devuelve null si el texto no es de esta institución o no matchea.
 */
interface InstitutionParser {
    val institution: Institution
    val version: Int
    fun parse(text: String, occurredAt: Long): ParsedTransaction?
}
