# DESIGN.md — TuBolsillo

Sistema visual de TuBolsillo (app móvil Kotlin Multiplatform / Compose Multiplatform).
Lo leen los agentes de IA (Claude Code, Stitch, etc.) para generar UI consistente.
Aplicación: finanzas personales para Venezuela/Panamá — **confianza primero**.

## 1. Design Read

- **Tipo**: app móvil product (finanzas personales, dashboard denso de datos).
- **Audiencia**: consumidor individual (es-VE), sensible a la confianza y la claridad del dinero.
- **Lenguaje**: "papel + bosque" — editorial-financiero cálido, NO tech oscuro, NO gradientes AI,
  NO glassmorphism. Se siente como un estado de cuenta bien diseñado, no como un casino.
- **Diales**: DESIGN_VARIANCE 4 · MOTION_INTENSITY 2 · VISUAL_DENSITY 5.

## 2. Color (tokens)

**Luz:**
- `background #F6F4EF` (papel cálido, nunca blanco puro)
- `surface #FFFFFF` · `surfaceVariant #ECE9E1`
- `ink #221F1A` (tinta cálida) · `muted #6F6A60`
- `primary #2E5E43` (verde bosque, único acento) · `onPrimary #FFFFFF`
- `primaryContainer #DCE8DF` · `onPrimaryContainer #123326`
- `secondary #6B705F` (oliva, iconos suaves) · `tertiary #B08A3E` (dorado, alertas/pago móvil)
- `error #A63D36` (terracota, solo deuda/TDC)

**Oscuro:**
- `background #131410` · `surface #1C1D16` · `ink #ECE9E2` · `muted #B9B4A8`
- `primary #9CC5A8` · `primaryContainer #23432F`
- `error #E3A29A`

**Semántica de montos:** positivo = `primary` · negativo = `error` · deuda BNPL siempre neutro
(el verde del disponible comunica salud). **Un solo acento**: no mezclar verde+ámbar+teal.

## 3. Tipografía

- Familia: Sans Serif del sistema (Roboto/SF). No cargar webfonts.
- Jerarquía: ExtraBold 38sp display (`-0.8` tracking) → Bold 28 → SemiBold 18 → Medium 15 → 14 → 11.
- **Regla oro: números tabulares** (`fontFeatureSettings = "tnum"`) en TODO monto.
- Labels en versalitas con tracking amplio (`letterSpacing ≥ 1.4x`) para eyebrows y cabeceras de sección.
- Nunca usar solo Regular/Bold: introducir 500/600 para jerarquía fina.

## 4. Superficie y layout

- Tarjetas: `RoundedCornerShape(28)`; borde hairline 1dp del acento a 30-55% alpha; **fondo
  tintado con el color de la cuenta** (blend 7-16% sobre surface). Sin sombras pesadas.
- Chip de cuenta: círculo 40dp con monograma de la institución (B, BV, MP…) en el color, sobre
  contenedor al 12% alpha. Los logos reales no se usan (licencias); el monograma es la marca.
- Espaciado base 4dp; pantallas con padding 16; gaps entre tarjetas 10-12.
- Headers de sección: versalitas espaciadas + hairline opcional — nunca títulos centrados.
- FAB: extended, redondeado, única acción primaria por pantalla.
- Header de app: 52dp compacto, título izquierda, sin appbar M3 inflada.

## 5. Componentes clave

| Componente | Regla |
|---|---|
| Hero dashboard | eyebrow "TOTAL EN CUENTAS" caps; monto 38sp tnum; sublínea VES muted; fila "Deudas $X · Neto $Y" (deuda en error, neto muted) |
| AccountCard (activo) | monograma + nombre + institución caps + saldo tnum + equiv USD si ≠ USD |
| DebtCard (TDC) | monograma + saldo (deuda) en error + barra de USO (deuda/límite) error |
| DebtCard (BNPL/Cashea) | monograma + **"Disponible" grande en primary** + barra de DISPONIBLE primaria (casi llena = sano) + "Deuda $X · de límite $Y" neutro |
| TxRow | icono fuente 15dp al 55%, descripción Medium 15, meta caps muted, monto tnum (±color) |
| Diálogo crear elemento | tipos por scroll horizontal; **Cashea siempre USD** con líneas Principal/Cotidiana, campos "Límite de la línea" → "Saldo disponible"; TDC "Límite" → "Deuda"; "Otra" con nombre libre; selector de color (10 dots) |

## 6. Reglas anti-slop (contextuales)

- Sin gradientes, sin glassmorphism, sin sombras negras, sin acentos saturados múltiples.
- Sin emojis en UI de producción.
- Montos SIEMPRE tnum y con símbolo consistente (Bs. / $ / ₿) del `MoneyFormatter`.
- Deuda BNPL: la salud la da el verde del disponible; no marcar deuda pequeña en rojo.
- Respetar el modelo mental del usuario: TDC ≠ cuenta, Cashea = líneas (no cuentas), todo en USD.
- El color de la cuenta personaliza tarjeta+monograma+borde; no cambiar colores semánticos globales.
