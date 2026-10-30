package dev.tubolsillo.app.data.source.portal

import dev.tubolsillo.app.domain.model.Institution

/**
 * Catálogo de recetas por banco. Cada entrada configura selectores de login,
 * selectores de tabla/SPA, formato de fecha, zona horaria y política de signo.
 */
object PortalRecipes {

    val GENERIC_TABLE = PortalRecipe(
        institution = Institution.OTHER,
        version = 2,
        rowsSelector = "table tr, mat-row, [role=row], .collapsible-div, .collection-item, .movimiento-item, .movement-row, .transaction-row",
        signPolicy = SignPolicy.HINTS_FROM_DESCRIPTION,
        dateFormat = "dd/MM/yyyy",
    )

    val BANESCO = PortalRecipe(
        institution = Institution.BANESCO,
        version = 3,
        userSelector = "#txtUsuario, input[name=txtUsuario], input[name=username], input[id*=Usuario i], input[id*=user i]",
        passSelector = "#txtClave, input[name=txtClave], input[type=password]",
        submitSelector = "#bAceptar, input[name=bAceptar], input.LogButton, button[type=submit], button.btn-primary, input[type=submit]",
        rowsSelector = "table tr, mat-row, [role=row], .movimiento-item, .movement-row",
        balanceSelector = "[id*=Saldo i], [class*=saldo i], td.Monto, .saldo-disponible",
        signPolicy = SignPolicy.DEBIT_CREDIT_COLUMNS,
        dateFormat = "dd/MM/yyyy",
        currency = "VES",
        timeZoneId = "America/Caracas",
    )

    val BANCARIBE = PortalRecipe(
        institution = Institution.BANCARIBE,
        version = 2,
        userSelector = "#userlogin, input[name=userlogin]",
        passSelector = "#passwd, input[name=passwd], input[type=password]",
        submitSelector = "button[name=submit], button[type=submit]",
        rowsSelector = "table tr, .collapsible-div, .collapsible-header, .collapsible li, .collection-item, .card-content .row, #main .row, #form .row, [role=row]",
        balanceSelector = "[id*=saldo i], [class*=saldo i], .monto-disponible, .saldo-disponible",
        signPolicy = SignPolicy.DEBIT_CREDIT_COLUMNS,
        dateFormat = "dd/MM/yyyy",
        currency = "VES",
        timeZoneId = "America/Caracas",
    )

    val BNC = PortalRecipe(
        institution = Institution.BNC,
        version = 3,
        // Paso 1 (#Frm_PreLogin): Número de tarjeta + Cédula -> Continuar.
        // Paso 2 (#Frm_Login, inyectado por AJAX en #FormContainer): #UserPassword -> Continuar.
        userSelector = "#CardNumber, input[name=CardNumber]",
        idSelector = "#UserID, input[name=UserID]",
        passSelector = "#UserPassword, input[name=UserPassword], input[type=password]",
        submitSelector = "#BtnSend, button[type=submit]",
        rowsSelector = "table tr, [role=row]",
        signPolicy = SignPolicy.DEBIT_CREDIT_COLUMNS,
        dateFormat = "dd/MM/yyyy",
        currency = "VES",
        timeZoneId = "America/Caracas",
    )

    val BDV = PortalRecipe(
        institution = Institution.BDV,
        version = 2,
        userSelector = "input[formcontrolname*=user i], input[formcontrolname*=usuario i], input[type=text]",
        passSelector = "input[type=password]",
        submitSelector = "button[type=submit], button.mat-raised-button, button.mat-flat-button",
        rowsSelector = "mat-row, table tr, [role=row]",
        signPolicy = SignPolicy.AMOUNT_SIGNED,
        dateFormat = "dd/MM/yyyy",
        currency = "VES",
        timeZoneId = "America/Caracas",
    )

    val MERCANTIL_VE = PortalRecipe(
        institution = Institution.MERCANTIL_VE,
        version = 2,
        userSelector = "input[formcontrolname*=user i], input[id*=user i], input[type=text]",
        passSelector = "input[type=password]",
        submitSelector = "button[type=submit], button.btn-primary, button.mat-flat-button",
        rowsSelector = "mat-row, table tr, [role=row], .movimiento-row",
        signPolicy = SignPolicy.AMOUNT_SIGNED,
        dateFormat = "dd/MM/yyyy",
        currency = "VES",
        timeZoneId = "America/Caracas",
    )

    val MERCANTIL_PA = PortalRecipe(
        institution = Institution.MERCANTIL_PA,
        version = 2,
        userSelector = "#UserName, input[name=UserId]",
        passSelector = "input[type=password]",
        submitSelector = "button.btn-login, button[name=login], button[type=submit]",
        rowsSelector = "table tr, [role=row]",
        signPolicy = SignPolicy.DEBIT_CREDIT_COLUMNS,
        dateFormat = "dd/MM/yyyy",
        currency = "USD",
        timeZoneId = "America/Panama",
    )

    val TODOTICKET = PortalRecipe(
        institution = Institution.TODOTICKET,
        version = 2,
        userSelector = "input[name=username], input[name=email], input[type=text], input[type=email]",
        passSelector = "input[type=password]",
        submitSelector = "button[type=submit]",
        rowsSelector = "table tr, [role=row], .movement-row, .transaction-item",
        signPolicy = SignPolicy.AMOUNT_SIGNED,
        dateFormat = "dd/MM/yyyy",
        currency = "VES",
        timeZoneId = "America/Caracas",
    )

    val all: List<PortalRecipe> = listOf(
        BANESCO,
        BANCARIBE,
        BNC,
        BDV,
        MERCANTIL_VE,
        MERCANTIL_PA,
        TODOTICKET,
    )

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
