package dev.tubolsillo.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.ui.graphics.vector.ImageVector

/** Una opción del selector de iconos de categoría. */
data class CategoryIcon(val key: String, val label: String, val vector: ImageVector)

/**
 * Iconos que puede llevar una categoría. La clave se guarda en
 * `categories.iconKey` (Room v7) y se resuelve aquí, no al revés: la base
 * nunca guarda un recurso ni un vector, solo un texto corto.
 */
object CategoryIcons {

    val options: List<CategoryIcon> = listOf(
        CategoryIcon("food", "Comida", Icons.Filled.Restaurant),
        CategoryIcon("coffee", "Café", Icons.Filled.Coffee),
        CategoryIcon("grocery", "Mercado", Icons.Filled.LocalGroceryStore),
        CategoryIcon("transport", "Transporte", Icons.Filled.DirectionsCar),
        CategoryIcon("health", "Salud", Icons.Filled.MedicalServices),
        CategoryIcon("pharmacy", "Farmacia", Icons.Filled.LocalPharmacy),
        CategoryIcon("gym", "Gimnasio", Icons.Filled.FitnessCenter),
        CategoryIcon("fun", "Entretenimiento", Icons.Filled.Movie),
        CategoryIcon("music", "Música", Icons.Filled.MusicNote),
        CategoryIcon("games", "Juegos", Icons.Filled.SportsEsports),
        CategoryIcon("home", "Hogar", Icons.Filled.Home),
        CategoryIcon("services", "Servicios", Icons.Filled.Bolt),
        CategoryIcon("education", "Educación", Icons.Filled.School),
        CategoryIcon("clothes", "Ropa", Icons.Filled.Checkroom),
        CategoryIcon("shopping", "Compras", Icons.Filled.ShoppingBag),
        CategoryIcon("travel", "Viajes", Icons.Filled.Flight),
        CategoryIcon("pets", "Mascotas", Icons.Filled.Pets),
        CategoryIcon("kids", "Niños", Icons.Filled.ChildCare),
        CategoryIcon("bills", "Suscripciones", Icons.Filled.Subscriptions),
        CategoryIcon("money", "Ahorro", Icons.Filled.Savings),
        CategoryIcon("fees", "Comisiones", Icons.Filled.Payments),
        CategoryIcon("tools", "Herramientas", Icons.Filled.Build),
    )

    private val byKey = options.associateBy { it.key }

    /** Vector para la clave guardada, o `null` si no hay icono (o es desconocida). */
    fun vector(key: String?): ImageVector? = key?.let { byKey[it]?.vector }

    fun label(key: String?): String? = key?.let { byKey[it]?.label }
}
