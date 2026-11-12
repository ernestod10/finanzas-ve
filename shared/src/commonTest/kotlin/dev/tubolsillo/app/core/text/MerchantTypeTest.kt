package dev.tubolsillo.app.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MerchantTypeTest {

    @Test
    fun `reconoce los rubros tipicos de un sms venezolano`() {
        assertEquals("Farmacia", MerchantType.of("FARMACIA DON JUAN"))
        assertEquals("Farmacia", MerchantType.of("FARMATODO, C.A."))
        assertEquals("Restaurante", MerchantType.of("RESTAURANT CHAO"))
        assertEquals("Restaurante", MerchantType.of("PIZZERIA DEL VALLE"))
        assertEquals("Supermercado", MerchantType.of("SUPERMERCADO PLAZA'S"))
        assertEquals("Supermercado", MerchantType.of("CENTRAL MADEIRENSE"))
        assertEquals("Café", MerchantType.of("CAFETERIA ITALIA"))
        assertEquals("Gasolinera", MerchantType.of("ESTACION DE SERVICIO PDV"))
    }

    @Test
    fun `el delivery gana sobre el transporte`() {
        assertEquals("Delivery", MerchantType.of("UBER EATS"))
        assertEquals("Delivery", MerchantType.of("RAPPI VENEZUELA"))
        assertEquals("Transporte", MerchantType.of("UBER *TRIP"))
        assertEquals("Transporte", MerchantType.of("TAXI AEROPUERTO"))
    }

    @Test
    fun `marcas antes que genericos`() {
        // "barberia" no debe caer en Restaurante ni "mercadolibre" en Supermercado.
        assertEquals("Barbería", MerchantType.of("BARBERIA EL REY"))
        assertEquals("Compra en línea", MerchantType.of("MERCADOLIBRE"))
        assertEquals("Suscripción", MerchantType.of("NETFLIX.COM"))
        assertEquals("Banco", MerchantType.of("PAGO MOVIL A JUAN"))
    }

    @Test
    fun `sin coincidencias devuelve null`() {
        assertNull(MerchantType.of("JUAN PEREZ"))
        assertNull(MerchantType.of(""))
        assertNull(MerchantType.of("   "))
    }
}
