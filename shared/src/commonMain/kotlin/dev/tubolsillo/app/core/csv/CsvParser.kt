package dev.tubolsillo.app.core.csv

/**
 * Parser CSV mínimo (subconjunto RFC-4180) en Kotlin puro:
 * campos con comillas, comillas escapadas (""), delimitador configurable y
 * saltos de línea dentro de campos entrecomillados. Reemplaza a kotlin-csv
 * (JVM-only) y funciona igual en Android e iOS.
 */
object CsvParser {

    fun readAll(text: String, delimiter: Char = ','): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            rows.add(row)
            row = mutableListOf()
        }

        while (i < n) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' -> {
                        if (i + 1 < n && text[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    }
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == delimiter -> endField()
                c == '\r' -> {
                    if (i + 1 < n && text[i + 1] == '\n') i++
                    endRow()
                }
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        // Última fila sin salto final.
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
