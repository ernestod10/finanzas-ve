package dev.tubolsillo.app.core.text

/**
 * Rubro del comercio deducido del texto del movimiento: "FARMACIA DON JUAN"
 * → "Farmacia", "RESTAURANT CHAO" → "Restaurante". El clasificador muestra el
 * rubro en vez del nombre completo, que en el SMS viene con sucursal, cédula
 * y ruido del banco.
 *
 * Es una tabla de palabras clave, no IA: determinista, instantánea y testeable.
 * Gana la primera regla que coincida, así que las marcas concretas van antes
 * que las palabras genéricas.
 */
object MerchantType {

    /** Etiqueta visible → fragmentos normalizados que la disparan. */
    private val rules: List<Pair<String, List<String>>> = listOf(
        "Delivery" to listOf("rappi", "pedidosya", "ubereats", "delivery", "glovo", "mando"),
        "Transporte" to listOf(
            "uber", "cabify", "indriver", "yandex", "taxi", "transporte", "metro", "autobus",
            "estacionamiento", "peaje", "porpuesto",
        ),
        "Gasolinera" to listOf("gasolinera", "combustible", "gasolina", "estaciondeservicio", "pdv"),
        "Farmacia" to listOf("farmacia", "farmatodo", "locatel", "drogueria", "farmahorro"),
        "Barbería" to listOf(
            "barberia", "barber", "peluqueria", "salon", "estetica", "spa", "belleza", "unas",
        ),
        "Cine" to listOf("cine", "cinemas", "cinex", "cinepolis", "teatro"),
        "Compra en línea" to listOf(
            "amazon", "mercadolibre", "shein", "aliexpress", "ebay", "temu", "alibaba",
        ),
        "Suscripción" to listOf(
            "netflix", "spotify", "youtube", "disney", "hbo", "prime", "chatgpt", "claude",
            "openai", "icloud", "googleone", "suscripcion",
        ),
        "Supermercado" to listOf(
            "supermercado", "automercado", "centralmadeirense", "excito", "garzon", "forum",
            "diaadia", "abasto", "mercado", "gama", "makro",
        ),
        "Restaurante" to listOf(
            "restaurante", "restaurant", "parrilla", "sushi", "pizzeria", "pizza", "burger",
            "hamburguesa", "arepa", "chao", "mcdonald", "kfc", "subway", "wendy", "dominos",
            "food", "comida", "grill", "cantina", "bistro", "tacos", "pollo",
        ),
        "Café" to listOf(
            "cafe", "cafeteria", "starbucks", "coffee", "panaderia", "bakery", "donuts",
            "heladeria", "helado", "pasteleria",
        ),
        "Servicios" to listOf(
            "cantv", "movistar", "digitel", "corpoelec", "electricidad", "hidrocapital",
            "gasdirecto", "telefonia", "internet", "aseguradora", "seguro", "condominio",
        ),
        "Banco" to listOf(
            "banco", "banesco", "mercantil", "bancaribe", "bnc", "bdv", "provincial", "pagomovil",
        ),
        "Salud" to listOf(
            "clinica", "consultorio", "laboratorio", "medico", "dentista", "odontolog",
            "hospital", "salud",
        ),
        "Educación" to listOf(
            "colegio", "escuela", "universidad", "academia", "curso", "educacion", "instituto",
        ),
        "Gimnasio" to listOf("gimnasio", "gym", "fitness", "crossfit", "bodytech", "entrenador"),
        "Viaje" to listOf(
            "hotel", "posada", "aerolinea", "vuelo", "avianca", "booking", "airbnb", "laser",
        ),
        "Veterinaria" to listOf("veterinaria", "mascota", "petshop", "animal"),
        "Ferretería" to listOf("ferreteria", "herramientas", "homedepot", "sodimac"),
        "Electrónica" to listOf(
            "electronica", "tecnology", "samsung", "iphone", "computadora", "laptop", "gadget",
        ),
        "Ropa" to listOf("ropa", "boutique", "zapateria", "moda", "jeans", "zara", "bershka"),
        "Bodega" to listOf("bodega", "minimarket", "kiosko", "tienda", "shop", "store"),
        "Apuestas" to listOf("casino", "lotto", "apuesta", "quiniela"),
    )

    /**
     * Rubro del comercio, o `null` si el texto no se parece a ninguno conocido.
     * El llamador decide qué mostrar en ese caso (normalmente el texto original).
     */
    fun of(description: String): String? {
        val text = MerchantNormalizer.normalize(description)
        if (text.isEmpty()) return null
        rules.forEach { (label, keywords) ->
            if (keywords.any { text.contains(it) }) return label
        }
        return null
    }
}
