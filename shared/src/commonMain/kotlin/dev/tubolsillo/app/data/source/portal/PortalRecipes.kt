package dev.tubolsillo.app.data.source.portal

import dev.tubolsillo.app.domain.model.Institution

/**
 * Catálogo de recetas. Soportar un banco nuevo es añadir una entrada aquí,
 * escrita contra el HTML real que se vuelca desde el teléfono.
 *
 * Mientras un banco no tenga receta propia usa [GENERIC_TABLE], que busca
 * cualquier tabla con filas que tengan fecha + monto. Sirve para validar el
 * arnés completo antes de conocer el DOM de cada portal.
 */
object PortalRecipes {

    val GENERIC_TABLE = PortalRecipe(
        institution = Institution.OTHER,
        version = 1,
        rowsSelector = "table tr",
        signPolicy = SignPolicy.HINTS_FROM_DESCRIPTION,
        dateFormat = "dd/MM/yyyy",
    )

    /** Recetas específicas por banco (se llenan con HTML real, etapa 2). */
    val all: List<PortalRecipe> = emptyList()

    fun forInstitution(institution: Institution): PortalRecipe? {
        if (institution.portalStartUrl == null) return null
        all.firstOrNull { it.institution == institution }?.let { return it }
        return GENERIC_TABLE.copy(
            institution = institution,
            currency = institution.defaultCurrency,
            timeZoneId = if (institution == Institution.MERCANTIL_PA) "America/Panama"
            else "America/Caracas",
        )
    }

    fun forKey(institutionKey: String): PortalRecipe? =
        forInstitution(Institution.fromKey(institutionKey))

    /** true si el banco ya tiene receta afinada (no la genérica). */
    fun hasTunedRecipe(institution: Institution): Boolean =
        all.any { it.institution == institution }
}
