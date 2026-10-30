# TuBolsillo — Sistema de diseño

Reescrito en oct-2026 a partir de las capturas de referencia de `screens/`
(app "Plata"): tarjetas redondeadas, verde jade, teselas de cuenta por marca,
métricas y gráficas. Sigue siendo Android con lenguaje iOS, pero la densidad y
la composición ahora son las de la referencia.

## 1. Principios

1. **Una cifra protagonista por pantalla.** El número grande manda; lo demás lo
   acompaña. En Inicio es el saldo en cuentas; en Estadísticas, el total del
   período.
2. **La tarjeta es la unidad.** Cada bloque de información vive en una tarjeta
   con radio 16, borde hairline y sombra suave. Nada flota suelto.
3. **El número es la interfaz.** Todo monto usa `TNUM`. El signo (`+`/`−`) y la
   flecha acompañan al color; nunca se comunica solo con color.
4. **El acento se gana su sitio.** El jade manda en acciones, cifras y estados.
   El ambiente es neutro.
5. **Nada se mueve solo.** Sin refrescos automáticos ni animaciones no pedidas.

## 2. Color

Semántico, por rol. Claro y oscuro definidos juntos.

| Rol | Claro | Oscuro |
|---|---|---|
| Lienzo | `#F5F8F8` | `#0A0C0B` |
| Tarjeta | `#FFFFFF` | `#141917` |
| Relleno (teselas, campos) | `#F2F5F4` | `#1C2421` |
| Borde de tarjeta | negro 6% | blanco 7% |
| Tinta | `#050505` | `#F7F9F8` |
| Tinta secundaria | `#6A707D` | `#A8B0AC` |
| Señal positiva | `#0E7A54` | `#5FD6A6` |
| Señal negativa | `#C0392B` | `#FF8A7A` |

**Acento Jade** (default; el usuario puede elegir otro en Menú → Apariencia):
- Texto e iconos: `#0E7A54` (5.3:1 sobre blanco ✅ AA).
- Rellenos y FAB: `#17A572`; gradiente del saludo `#17A572 → #2FB183`.
- El verde brillante **nunca** se usa como tinta sobre blanco (3.2:1).
- En oscuro: `#5FD6A6` (10.9:1 sobre `#0C0F0B`).

**Colores de marca por institución** (`ui/components/InstitutionVisuals.kt`):
Mercantil `#1F49B2`, BDV `#B92626`, Banesco `#1C7E3F`, Binance `#F59F19`,
Zinli `#7B3DEC`, Wally `#16A599`, Bancaribe `#0F6F9E`, BNC `#C8102E`,
Cashea `#2FA36B`. El color elegido por el usuario en la cuenta tiene prioridad;
si no hay ninguno, se reparte la paleta por nombre. La tinta sobre una tesela se
elige por contraste (`onAccent`), no se asume blanca.

## 3. Tipografía

Escala de iOS, sin cambios de familia.

| Estilo | Tamaño | Peso | Uso |
|---|---|---|---|
| `displayLarge` | 38 | Bold | Cifra protagonista (monto del alta) |
| `displayMedium` | 34 | Bold | Título grande de pantalla |
| `headlineLarge` | 28 | Bold | Título de pantalla |
| `headlineMedium` | 22 | Bold | Cifra de tarjeta de gráfica |
| `titleLarge` | 22 | Normal | Cifra secundaria |
| `titleMedium` | 17 | SemiBold | Título de tarjeta, fila |
| `titleSmall` | 15 | SemiBold | Cifra de tesela, chips |
| `bodyLarge` | 17 | Normal | Cuerpo |
| `bodyMedium` | 15 | Normal | Texto secundario |
| `bodySmall` | 13 | Normal | Meta, pie de tarjeta |
| `labelSmall` | 13 | Medium | Rótulo en versalitas |

`TNUM = "tnum"` en toda cifra. En teselas se usa código de moneda + monto
(`VES 160.000,00`) porque el símbolo completo no entra en tres columnas.

## 4. Espaciado y forma

- Margen lateral: **16dp**. Título de pantalla y hero: 16dp (ya no 32).
- Tarjeta: radio **16dp**; tesela **12dp**; campos **14dp**; píldoras **50%**.
- Sombra de tarjeta: 6dp en claro, 3dp en oscuro (sobre negro la profundidad la
  da el borde). Nunca sombra negra dura.
- Fila: alto mínimo 44dp; controles táctiles ≥ 48dp.
- Grilla de cuentas: 3 columnas, separación 10dp, alto de tesela 96dp.

## 5. Material

- La tarjeta es superficie opaca con borde hairline y sombra suave.
- El cristal (`GlassSurface`) queda solo para la capa funcional que aún lo usa
  (barra superior de `IOSScreen`). La barra inferior del rediseño es opaca.
- Se permite **un** degradado por pantalla: el saludo de Inicio.
- Sin texturas, sin imágenes de fondo, sin glass sobre contenido.

## 6. Movimiento

- **Empujar pantalla**: slide horizontal 260ms; la anterior se corre un tercio.
- **Cambiar de destino**: el pager se mueve con el dedo (swipe) o salta por
  índice al tocar la barra. Sin animación de "carrusel" adicional.
- El título grande de `IOSScreen` colapsa con el scroll en las pantallas
  empujadas.

## 7. Navegación

- Barra inferior con cuatro destinos: **Inicio · Registros · (+) · Estadísticas ·
  Menú**. El `+` central no es un destino: abre el alta manual.
- Inicio tiene dos pestañas superiores con subrayado: **Cuentas |
  Presupuestos**.
- Lo empujado (detalle de cuenta, plan, tasas, nuevo registro…) no muestra la
  barra inferior y conserva "Volver".

## 8. Gráficas

- **Línea de saldo**: sin grilla ni eje Y; relleno degradado bajo la curva;
  etiquetas de fecha cada ~5 días; punto final marcado.
- **Dona de gasto**: hueco de 2° entre segmentos; el dato principal va en el
  centro; leyenda de dos columnas con el texto en tinta, nunca en el color de la
  serie.
- La serie de saldo usa las capturas reales y la tasa de hoy (está rotulado).
  No se inventan puntos.

## 9. Reglas anti-slop

- Un solo acento por pantalla.
- Sin emojis en la interfaz.
- Sin sombras negras duras ni degradados fuera del saludo.
- El número grande de una línea de crédito es el **disponible**, no la deuda.
- TDC ≠ cuenta; Cashea = línea de crédito; todo en USD para comparar.
- Cada número dice qué es: saldo registrado, flujo del período o límite de
  presupuesto. No se mezclan.
