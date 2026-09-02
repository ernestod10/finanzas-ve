package dev.tubolsillo.app.domain.model

/**
 * Instituciones conectables. La clave se guarda en la base de datos.
 *
 * [notificationPackages]: packages de las apps cuyas notificaciones captura el
 * NotificationListener (verificados en la investigación de sep-2026).
 * [smsSenderPatterns]: substrings (lowercase) que identifican al remitente de
 * un SMS ("banesco", "mercantil"…), usado cuando el texto no tiene keyword.
 */
enum class Institution(
    val key: String,
    val displayName: String,
    val notificationPackages: List<String> = emptyList(),
    val smsSenderPatterns: List<String> = emptyList(),
) {
    BANESCO(
        "BANESCO", "Banesco",
        notificationPackages = listOf("com.banesco.samfbancamovilunificada"),
        smsSenderPatterns = listOf("banesco"),
    ),
    MERCANTIL_PA(
        "MERCANTIL_PA", "Mercantil Panamá",
        notificationPackages = listOf("com.pa.mercantilbanco.mercantilmovil"),
        smsSenderPatterns = listOf("mercantil"),
    ),
    BANCARIBE(
        "BANCARIBE", "Bancaribe",
        notificationPackages = listOf("bancaribe.miconexion"),
        smsSenderPatterns = listOf("bancaribe"),
    ),
    BNC(
        "BNC", "BNC",
        notificationPackages = listOf("bnc.bncnet.mobile2"),
        smsSenderPatterns = listOf("bnc"),
    ),
    BDV(
        "BDV", "Banco de Venezuela",
        notificationPackages = listOf("com.bancodevenezuela.bdvdigital"),
        smsSenderPatterns = listOf("bdv", "banco de venezuela", "bdvenlinea"),
    ),
    CASHEA(
        "CASHEA", "Cashea",
        notificationPackages = listOf("com.cashea.app"),
        smsSenderPatterns = listOf("cashea"),
    ),
    BINANCE(
        "BINANCE", "Binance",
        notificationPackages = listOf("com.binance.dev"),
    ),
    OTHER("OTHER", "Otra");

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
