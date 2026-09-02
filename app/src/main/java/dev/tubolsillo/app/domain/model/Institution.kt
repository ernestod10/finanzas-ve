package dev.tubolsillo.app.domain.model

/**
 * Instituciones conectables. La clave se guarda en la base de datos.
 * Fase 3 agregará aquí los patrones de sender SMS y packages de notificación
 * de cada institución para el enrutado de capturas.
 */
enum class Institution(val key: String, val displayName: String) {
    BANESCO("BANESCO", "Banesco"),
    MERCANTIL_PA("MERCANTIL_PA", "Mercantil Panamá"),
    BANCARIBE("BANCARIBE", "Bancaribe"),
    BNC("BNC", "BNC"),
    BDV("BDV", "Banco de Venezuela"),
    CASHEA("CASHEA", "Cashea"),
    BINANCE("BINANCE", "Binance"),
    OTHER("OTHER", "Otra");

    companion object {
        fun fromKey(key: String?): Institution =
            entries.firstOrNull { it.key == key } ?: OTHER
    }
}
