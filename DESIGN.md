# TuBolsillo — Sistema de diseño

Reescrito con el skill `apple-design` (HIG de Apple). Android, pero con el
lenguaje visual de iOS: las bases (tipografía, color, layout, materiales)
vienen de las HIG; las convenciones de plataforma se aplican donde el usuario
las reconoce.

## 1. Principios

1. **Una capa funcional y una de contenido.** El cristal vive solo en barras,
   hojas y diálogos. Nunca sobre tarjetas, filas ni fondos.
   (`liquid-glass.md › Review checklist`)
2. **El acento se gana su sitio.** El verde manda en acciones y cifras; el
   ambiente es neutro. `branding.md`: la marca cede ante el contenido.
3. **El número es la interfaz.** Todo monto en `tnum`; el signo se dice con
   `+/−`, color y posición, nunca solo con color.
4. **Nada se mueve solo.** Sin refrescos automáticos, sin animaciones no pedidas.

## 2. Color

Semántico, por rol y no por apariencia (`color.md › System colors`).

| Rol | Claro | Oscuro |
|---|---|---|
| Fondo agrupado | `#F2F2F7` | `#000000` |
| Superficie (filas) | `#FFFFFF` | `#1C1C1E` |
| Relleno terciario | `#E5E5EA` | `#2C2C2E` |
| Tinta | `#000000` | `#FFFFFF` |
| Tinta secundaria | `#5A5A5E` | `#A0A0A5` |
| Separador | `#C6C6C8` | `#38383A` |
| Acento (marca) | `#2A7C13` | `#76C457` |
| Error / deuda | `#B03A2E` | `#FF9E90` |

**Contraste:** los 18 pares de texto pasan WCAG AA. El más ajustado es el acento
claro sobre el fondo agrupado, 4.72:1.

> El `secondaryLabel` real de iOS queda en ~3.5:1 y no pasa AA. Aquí se usa un
> gris equivalente más oscuro (6.15:1) para cumplir sin perder la jerarquía.

## 3. Tipografía

Escala de iOS, tamaño por defecto *Large* (`typography.md › Specifications`).

| Estilo | Tamaño | Peso | Uso |
|---|---|---|---|
| Large Title | 34 | Bold | Título de pantalla, dentro del scroll |
| Title 1 | 28 | Bold | Encabezado de sección grande |
| Title 2 | 22 | Regular | Subtítulo |
| Headline | 17 | SemiBold | Título de barra inline, nombre de fila |
| Body | 17 | Regular | Cuerpo: el tamaño de lectura de iOS |
| Subhead | 15 | Regular | Texto secundario |
| Footnote | 13 | Regular | Meta, encabezado de grupo en versalitas |
| Caption | 12/11 | Regular | Pie, etiquetas |

Los títulos van en **Regular/Bold puntual**, no en ExtraBold: el énfasis se gana
con un peso, no con todos. El tracking editorial fuerte se retiró.

## 4. Espaciado y forma

- Margen lateral: **16dp** para grupos, **32dp** para el título grande y el hero
  (el texto alinea con el contenido de las filas, no con su borde).
- Grupos: radio **10dp**, una sola superficie continua por grupo.
- Separadores: hairline 0.5dp, sangrados 16dp (o 68dp si la fila tiene icono).
- Fila: alto mínimo **44dp** (`accessibility.md`: default 44×44pt en iOS).
- Barra de navegación: 44dp de contenido + safe area, título centrado 17/SemiBold.
- Barra de pestañas: cápsula flotante glass, radio 26dp, sin sombra.

## 5. Material

`GlassSurface`: relleno translúcido al 78%, borde de brillo hairline de 0.5dp, y
**sin sombra proyectada** — la profundidad la da el material, no la sombra.

**Límite real:** iOS desenfoca lo que hay *detrás* de la barra. Android no expone
*backdrop blur* para vistas dentro de una Activity, así que el cristal aquí es
translúcido, no desenfocado. `TubolsilloGlass.reduceTransparency` fuerza relleno
opaco (fallback de la checklist de accesibilidad).

## 6. Movimiento

- **Empujar pantalla:** slide horizontal. La nueva entra desde la derecha, la
  anterior se corre un tercio y no se desvanece; al volver se invierte.
  `PUSH_MS = 340`, `FastOutSlowInEasing`.
- **Cambiar de pestaña:** sin animación. En iOS el contenido simplemente
  aparece; deslizar entre pestañas es una convención de Android.
- **Colapsar el título:** el título grande se va con el scroll y la barra inline
  aparece al 55% del recorrido.

## 7. Reglas anti-slop

- Sin degradados, sin glassmorphism en el contenido, sin sombras negras.
- Sin emojis en la interfaz.
- Un solo acento por pantalla.
- El número grande de una línea de crédito es el **disponible**, no la deuda.
- TDC ≠ cuenta; Cashea = líneas de crédito, no cuentas; todo en USD.
