package dev.tubolsillo.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.domain.model.AccountBalanceView
import dev.tubolsillo.app.resources.Res
import dev.tubolsillo.app.resources.logo_activo
import dev.tubolsillo.app.resources.logo_banesco
import dev.tubolsillo.app.resources.logo_banco_plaza
import dev.tubolsillo.app.resources.logo_bancaribe
import dev.tubolsillo.app.resources.logo_bancrecer
import dev.tubolsillo.app.resources.logo_banplus
import dev.tubolsillo.app.resources.logo_bdv
import dev.tubolsillo.app.resources.logo_binance
import dev.tubolsillo.app.resources.logo_bnc
import dev.tubolsillo.app.resources.logo_bod
import dev.tubolsillo.app.resources.logo_cashea
import dev.tubolsillo.app.resources.logo_cestaticket
import dev.tubolsillo.app.resources.logo_del_sur
import dev.tubolsillo.app.resources.logo_exterior
import dev.tubolsillo.app.resources.logo_mercantil
import dev.tubolsillo.app.resources.logo_provincial
import dev.tubolsillo.app.resources.logo_sofitasa
import dev.tubolsillo.app.resources.logo_todoticket
import dev.tubolsillo.app.resources.logo_ubii
import dev.tubolsillo.app.resources.logo_wally
import dev.tubolsillo.app.resources.logo_zinli
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Logo empaquetado de la entidad, si lo tenemos. Los fondos se recortaron y
 * se normalizaron a 128×128 con transparencia; las que faltan caen al
 * monograma de iniciales.
 */
fun institutionLogo(institutionKey: String): DrawableResource? = when (institutionKey) {
    "BANESCO" -> Res.drawable.logo_banesco
    "BANCARIBE" -> Res.drawable.logo_bancaribe
    "BNC" -> Res.drawable.logo_bnc
    "MERCANTIL_VE", "MERCANTIL_PA" -> Res.drawable.logo_mercantil
    "BINANCE" -> Res.drawable.logo_binance
    "ZINLI" -> Res.drawable.logo_zinli
    "WALLY" -> Res.drawable.logo_wally
    "UBII" -> Res.drawable.logo_ubii
    "BANCO_PLAZA" -> Res.drawable.logo_banco_plaza
    "CASHEA" -> Res.drawable.logo_cashea
    "BANPLUS" -> Res.drawable.logo_banplus
    "TODOTICKET" -> Res.drawable.logo_todoticket
    "BOD" -> Res.drawable.logo_bod
    "BDV" -> Res.drawable.logo_bdv
    "PROVINCIAL" -> Res.drawable.logo_provincial
    "EXTERIOR" -> Res.drawable.logo_exterior
    "SOFITASA" -> Res.drawable.logo_sofitasa
    "ACTIVO" -> Res.drawable.logo_activo
    "CESTATICKET" -> Res.drawable.logo_cestaticket
    "DEL_SUR" -> Res.drawable.logo_del_sur
    "BANCRECER" -> Res.drawable.logo_bancrecer
    // Banfanb, Banco Caroní y Mi Banco no publican un logo descargable (sus
    // sitios no responden); caen al monograma con su color de marca.
    else -> null
}

/**
 * Color de marca por institución, medido de las capturas de referencia.
 * La app no usa logos (licencias): el color + el monograma hacen de identidad.
 */
val InstitutionBrandColors: Map<String, Color> = mapOf(
    "BANESCO" to Color(0xFF1C7E3F),
    "BANCARIBE" to Color(0xFF0F6F9E),
    "BNC" to Color(0xFFC8102E),
    "BDV" to Color(0xFFB92626),
    "BANCO_PLAZA" to Color(0xFF6E2C8F),
    "BOD" to Color(0xFF0E5AA7),
    "PROVINCIAL" to Color(0xFF1B5E9E),
    "EXTERIOR" to Color(0xFF007A33),
    "DEL_SUR" to Color(0xFF0B7A75),
    "CARONI" to Color(0xFFC2410C),
    "SOFITASA" to Color(0xFF8A1C3B),
    "BANPLUS" to Color(0xFF1D4E89),
    "BANCO_100" to Color(0xFF7A1FA2),
    "BANFANB" to Color(0xFF1E6B3A),
    "ACTIVO" to Color(0xFFB45309),
    "MI_BANCO" to Color(0xFF0F766E),
    "BANCRECER" to Color(0xFF2563EB),
    "MERCANTIL_VE" to Color(0xFF1F49B2),
    "MERCANTIL_PA" to Color(0xFF1F49B2),
    "ZINLI" to Color(0xFF7B3DEC),
    "UBII" to Color(0xFF0A66C2),
    "WALLY" to Color(0xFF16A599),
    "TODOTICKET" to Color(0xFFE11D48),
    "CESTATICKET" to Color(0xFFF59E0B),
    "CASHEA" to Color(0xFF2FA36B),
    "BINANCE" to Color(0xFFF59F19),
    "OTHER" to Color(0xFF5E6B74),
)

/**
 * Color de una tesela de cuenta: manda la elección del usuario, después el
 * color de marca de la institución, y si no hay ninguno se reparte la paleta
 * por el nombre (estable entre sesiones, no aleatorio).
 */
fun accountAccent(view: AccountBalanceView): Color {
    parseAccountColor(view.colorHex)?.let { return it }
    InstitutionBrandColors[view.institutionKey]?.let { return it }
    val index = (view.name.hashCode() and Int.MAX_VALUE) % ACCOUNT_PALETTE.size
    return parseAccountColor(ACCOUNT_PALETTE[index]) ?: Color(0xFF5E6B74)
}

/**
 * Tinta sobre un color de marca: elige la que más contrasta, en vez de asumir
 * blanco. Sobre el ámbar de Binance el texto va oscuro; sobre el azul de
 * Mercantil, blanco.
 */
fun onAccent(color: Color): Color {
    val l = color.luminance()
    val whiteContrast = 1.05 / (l + 0.05)
    val darkContrast = (l + 0.05) / 0.056
    return if (darkContrast > whiteContrast) Color(0xFF101512) else Color.White
}

/**
 * Insignia de la entidad: su logo sobre un disco blanco si lo tenemos, o las
 * iniciales sobre el color de marca si no. La usan tanto las teselas como la
 * pantalla de carga de los portales.
 */
@Composable
fun InstitutionLogoBadge(
    institutionKey: String,
    displayName: String,
    size: Int = 34,
    modifier: Modifier = Modifier,
) {
    val brand = InstitutionBrandColors[institutionKey] ?: MaterialTheme.colorScheme.primary
    val logo = institutionLogo(institutionKey)
    if (logo != null) {
        Surface(
            shape = CircleShape,
            color = Color.White,
            modifier = modifier.size(size.dp),
        ) {
            Image(
                painter = painterResource(logo),
                contentDescription = displayName,
                modifier = Modifier
                    .padding((size * 0.14f).dp)
                    .fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(size.dp)
                .background(brand, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsFor(displayName),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = onAccent(brand),
                maxLines = 1,
            )
        }
    }
}
