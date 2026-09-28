package dev.tubolsillo.app.domain.model

/**
 * Instituciones conectables. La clave se guarda en la base de datos.
 *
 * [notificationPackages]: packages de las apps cuyas notificaciones captura el
 * NotificationListener (verificados en la investigación de sep-2026); los no
 * conocidos quedan vacíos y se mapean desde la pantalla de apps observadas.
 * [smsSenderPatterns]: substrings (lowercase) que identifican al remitente de
 * un SMS, usado cuando el texto no tiene keyword.
 * [defaultCurrency]: moneda típica, sugerida al crear la cuenta.
 */
enum class Institution(
    val key: String,
    val displayName: String,
    val notificationPackages: List<String> = emptyList(),
    val smsSenderPatterns: List<String> = emptyList(),
    val defaultCurrency: String = "VES",
    /** URL de inicio del portal web (para vinculación por WebView). */
    val portalStartUrl: String? = null,
) {
    // --- Bancos venezolanos ---
    BANESCO(
        "BANESCO", "Banesco",
        notificationPackages = listOf("com.banesco.samfbancamovilunificada"),
        smsSenderPatterns = listOf("banesco"),
        portalStartUrl = "https://banesconecta.banesco.com/Authenticate/Login",
    ),
    BANCARIBE(
        "BANCARIBE", "Bancaribe",
        notificationPackages = listOf("bancaribe.miconexion"),
        smsSenderPatterns = listOf("bancaribe"),
        portalStartUrl = "https://www.bancaribe.com.ve/",
    ),
    BNC(
        "BNC", "BNC",
        notificationPackages = listOf("bnc.bncnet.mobile2"),
        smsSenderPatterns = listOf("bnc", "banco nacional de credito"),
        portalStartUrl = "https://bncenlinea.com/",
    ),
    BDV(
        "BDV", "Banco de Venezuela",
        notificationPackages = listOf("com.bancodevenezuela.bdvdigital"),
        smsSenderPatterns = listOf("bdv", "banco de venezuela", "bdvenlinea"),
        portalStartUrl = "https://bdvenlinea.banvenez.com/",
    ),
    BANCO_PLAZA("BANCO_PLAZA", "Banco Plaza"),
    BOD("BOD", "BOD"),
    PROVINCIAL("PROVINCIAL", "Banco Provincial"),
    EXTERIOR("EXTERIOR", "Banco Exterior"),
    DEL_SUR("DEL_SUR", "Banco del Sur"),
    CARONI("CARONI", "Banco Caroní"),
    SOFITASA("SOFITASA", "Sofitasa"),
    BANPLUS("BANPLUS", "Banco Banplus"),
    BANCO_100("BANCO_100", "100% Banco"),
    BANFANB("BANFANB", "Banfanb"),
    ACTIVO("ACTIVO", "Banco Activo"),
    MI_BANCO("MI_BANCO", "Mi Banco"),
    BANCRECER("BANCRECER", "Bancrecer"),

    // --- Venezuela ---
    MERCANTIL_VE(
        "MERCANTIL_VE", "Mercantil Venezuela",
        notificationPackages = listOf("com.mercantilbanco.unicoempresas", "com.mercantilbanco.personas"),
        smsSenderPatterns = listOf("mercantil"),
        defaultCurrency = "VES",
        portalStartUrl = "https://www.mercantilbanco.com/",
    ),

    // --- Panamá ---
    MERCANTIL_PA(
        "MERCANTIL_PA", "Mercantil Panamá",
        notificationPackages = listOf("com.pa.mercantilbanco.mercantilmovil"),
        smsSenderPatterns = listOf("mercantil"),
        defaultCurrency = "USD",
        portalStartUrl = "https://www.mercantilbanco.com.pa/",
    ),

    // --- Wallets / fintech ---
    ZINLI("ZINLI", "Zinli", defaultCurrency = "USD"),
    UBII("UBII", "Ubii", defaultCurrency = "USD"),
    WALLY("WALLY", "Wally", defaultCurrency = "USD"),

    // --- Beneficios / tickets ---
    TODOTICKET(
        "TODOTICKET", "TodoTicket",
        portalStartUrl = "https://www.todoticket.com/",
    ),
    CESTATICKET("CESTATICKET", "CestaTicket"),

    // --- BNPL ---
    CASHEA(
        "CASHEA", "Cashea",
        notificationPackages = listOf("com.cashea.app"),
        smsSenderPatterns = listOf("cashea"),
        defaultCurrency = "USD",
    ),

    // --- Crypto ---
    BINANCE(
        "BINANCE", "Binance",
        notificationPackages = listOf("com.binance.dev"),
        defaultCurrency = "USDT",
    ),

    OTHER("OTHER", "Otra", defaultCurrency = "USD");

    companion object {
        fun fromKey(key: String?): Institution =
            entries.firstOrNull { it.key == key } ?: OTHER

        /** Institución cuyo package de notificaciones coincide, o null. */
        fun byNotificationPackage(packageName: String?): Institution? {
            if (packageName == null) return null
            return entries.firstOrNull { inst ->
                inst.notificationPackages.any { it.equals(packageName, ignoreCase = true) }
            }
        }

        /** Institución cuyo patrón de remitente SMS coincide, o null. */
        fun bySmsSender(sender: String?): Institution? {
            if (sender.isNullOrBlank()) return null
            val lower = sender.lowercase()
            return entries.firstOrNull { inst ->
                inst.smsSenderPatterns.any { lower.contains(it) }
            }
        }
    }
}
