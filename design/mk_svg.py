# Pantallas de TuBolsillo como SVG agrupados (una <g> por bloque, con nombre),
# listos para importar en Penpot o Figma y editar con el panel de capas.
W, H = 360, 780
BG, CARD, TILE, LINE = "#0E1110", "#171A18", "#1F2320", "#2A2F2B"
INK, MUTED, ACCENT, AINK, NEG, POS = "#E9ECEA", "#9AA09B", "#5FD6A6", "#04241A", "#F2796F", "#5FD6A6"
FONT = "Inter, 'Segoe UI', Roboto, system-ui, sans-serif"

def esc(s): return s.replace("&", "&amp;").replace("<", "&lt;")

def t(x, y, s, size=13, fill=INK, weight=400, anchor="start", ls=None):
    extra = f' letter-spacing="{ls}"' if ls else ""
    return (f'<text x="{x}" y="{y}" font-family="{FONT}" font-size="{size}" fill="{fill}" '
            f'font-weight="{weight}" text-anchor="{anchor}"{extra}>{esc(s)}</text>')

def r(x, y, w, h, fill, rad=0, stroke=None, sw=1):
    st = f' stroke="{stroke}" stroke-width="{sw}"' if stroke else ""
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rad}" fill="{fill}"{st}/>'

def line(x1, y1, x2, y2, col=LINE):
    return f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{col}"/>'

def g(gid, *parts):
    return f'<g id="{gid}">' + "".join(parts) + "</g>"

def nav(active):
    items = [("Inicio", 44), ("Registros", 120), ("Estadísticas", 250), ("Menú", 320)]
    p = [r(0, H-62, W, 62, "#0B0E0D"), r(0, H-62, W, 1, LINE),
         r(151, H-58, 58, 40, ACCENT, 14), t(180, H-30, "+", 22, AINK, 700, "middle")]
    for name, x in items:
        p.append(t(x, H-30, name, 10, ACCENT if name == active else MUTED, 700 if name == active else 400, "middle"))
    return g("nav-inferior", *p)

def frame(name, body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">'
            f'<g id="{name}"><rect width="{W}" height="{H}" fill="{BG}"/>' + body + "</g></svg>")

def big_title(x, y, text, sub=None):
    parts = [t(x, y, text, 30, INK, 800)]
    if sub: parts.append(t(x, y + 20, sub, 12, MUTED))
    return g("titulo", *parts)

def card_block(gid, x, y, w, h, *inner, rad=18):
    return g(gid, r(x, y, w, h, CARD, rad, LINE), *inner)

def label(text, x=20, y=0):
    return t(x, y, text.upper(), 10, MUTED, 700, ls="1")

def list_row(gid, x, y, w, title, detail, value=None, value_color=INK, h=52):
    p = [t(x + 16, y + 22, title, 14, INK, 500)]
    if detail: p.append(t(x + 16, y + 38, detail, 11, MUTED))
    if value: p.append(t(x + w - 16, y + 28, value, 13, value_color, 700, "end"))
    return g(gid, r(x, y, w, h, CARD, 14, LINE), *p)

# ---------- 1. INICIO ----------
p = [big_title(20, 64, "Inicio", "TuBolsillo · Finanzas personales")]
p.append(g("tabs", t(48, 124, "Cuentas", 13, INK, 700), r(40, 132, 62, 2, ACCENT),
           t(148, 124, "Presupuestos", 13, MUTED), t(262, 124, "Deudas", 13, MUTED)))
p.append(g("hero", r(16, 146, 328, 92, "#14402F", 22),
           t(32, 176, "Buenas tardes", 12, "#D8E9DF"),
           t(32, 204, "Tu dinero, claro y en orden.", 21, "#FFFFFF", 800)))
rates = [label("Tasas de hoy", 32, 278), t(292, 278, "Calculadora", 12, ACCENT, 700, "middle")]
for i, (k, v) in enumerate([("DÓLAR BCV", "Bs. 893,21"), ("EURO BCV", "Bs. 999,76"), ("USDT P2P", "Bs. 1.042,04")]):
    x = 28 + i * 104
    rates += [r(x, 290, 96, 40, TILE, 12), t(x + 8, 305, k, 8.5, MUTED, 600), t(x + 8, 321, v, 12, INK, 700)]
p.append(card_block("tasas", 16, 250, 328, 92, *rates))
p.append(card_block("saldo", 16, 354, 328, 74, label("Tu saldo en cuentas", 32, 378),
                    t(32, 402, "$ 1.092,43", 24, INK, 800), t(32, 418, "≈ Bs. 1.138.371,80", 11, MUTED)))
tiles = [("Bancaribe", "VES 12.594,11"), ("Banesco", "VES 15.480,25"), ("Bancaribe tdc", "VES 2.467,45"),
         ("Bnc credit", "VES 114.311,14"), ("Binance USDT", "USDT 4,71"), ("+", "")]
grid = []
for i, (nm, amt) in enumerate(tiles):
    x = 16 + (i % 3) * 112; y = 440 + (i // 3) * 92
    grid += [r(x, y, 104, 84, TILE, 14), t(x + 10, y + 34, nm, 10.5, INK, 600), t(x + 10, y + 66, amt, 11.5, INK, 800)]
p.append(g("grilla-cuentas", *grid))
p.append(nav("Inicio"))
open("svg/inicio.svg", "w").write(frame("inicio", "".join(p)))

# ---------- 2. REGISTROS ----------
p = [g("cabecera", t(20, 40, "‹ Volver", 13, ACCENT, 600), t(20, 80, "Registros", 30, INK, 800))]
p.append(g("busqueda", r(16, 96, 328, 44, TILE, 14, LINE),
           t(32, 123, "Buscar comercio, categoría o etiqueta…", 12, MUTED),
           t(20, 160, "Pregunta: «¿cuánto gasté en farmacia?»", 10.5, MUTED),
           t(300, 160, "Preguntar", 12, ACCENT, 700, "middle")))
rows = [("Hoy", None)]
tx = [("Farmacia San José", "Salud · 09:14", "− Bs. 1.240,00", NEG),
      ("Pago móvil · Oriv…", "Transferencia · 08:50", "+ Bs. 3.400,00", POS),
      ("Supermercado Central", "Comida · 08:02", "− Bs. 8.930,50", NEG)]
body = []
y = 190
for title, detail, value, col in tx:
    body.append(g("fila-" + title[:12].replace(" ", "-").lower(),
                  t(20, y + 20, title, 14, INK, 500), t(20, y + 38, detail, 11, MUTED),
                  t(340, y + 26, value, 13, col, 700, "end"), r(16, y + 52, 328, 1, LINE)))
    y += 60
p.append(g("dia-hoy", t(20, 186, "HOY", 10, MUTED, 700, ls="1"), *body))
p.append(nav("Registros"))
open("svg/registros.svg", "w").write(frame("registros", "".join(p)))

# ---------- 3. ESTADÍSTICAS ----------
p = [big_title(20, 64, "Estadísticas", "Cómo se movió tu dinero")]
p.append(g("selector-mes", r(16, 96, 328, 44, TILE, 14), t(28, 123, "‹", 15, MUTED),
           t(180, 123, "noviembre 2026", 13, INK, 700, "middle"), t(332, 123, "›", 15, MUTED, 400, "end")))
p.append(card_block("resumen-ia", 16, 152, 328, 96,
                    t(32, 178, "✦  Resumen del mes", 13, ACCENT, 700),
                    t(32, 200, "Un párrafo con lo que pasó en", 11, MUTED),
                    t(32, 216, "noviembre 2026, a partir de tus números.", 11, MUTED),
                    t(32, 236, "Generar resumen", 12, ACCENT, 700)))
pts = "32,430 90,420 150,398 210,404 268,372 328,356"
p.append(card_block("tendencia", 16, 260, 328, 190,
                    t(32, 288, "Tendencia del saldo", 13, INK, 700),
                    label("Saldo actual", 32, 312), t(32, 336, "$ 1.092,43", 22, INK, 800),
                    r(220, 300, 108, 34, "#3A2020", 12), t(274, 321, "−8 % vs. inicio", 10, NEG, 700, "middle"),
                    f'<polyline points="{pts}" fill="none" stroke="{ACCENT}" stroke-width="2.5"/>',
                    t(32, 440, "1 nov", 10, MUTED), t(328, 440, "30 nov", 10, MUTED, 400, "end")))
p.append(card_block("estructura", 16, 462, 328, 150,
                    t(32, 490, "Estructura de gastos", 13, INK, 700),
                    f'<circle cx="92" cy="560" r="34" fill="none" stroke="{ACCENT}" stroke-width="14" stroke-dasharray="120 94"/>',
                    f'<circle cx="92" cy="560" r="34" fill="none" stroke="{NEG}" stroke-width="14" stroke-dasharray="60 214" stroke-dashoffset="-120"/>',
                    t(150, 546, "● Comida", 11, INK), t(320, 546, "Bs. 8.930", 11, INK, 700, "end"),
                    t(150, 566, "● Transporte", 11, INK), t(320, 566, "Bs. 4.100", 11, INK, 700, "end"),
                    t(150, 586, "● Salud", 11, INK), t(320, 586, "Bs. 1.240", 11, INK, 700, "end")))
p.append(nav("Estadísticas"))
open("svg/estadisticas.svg", "w").write(frame("estadisticas", "".join(p)))

# ---------- 4. CRÉDITOS ----------
p = [g("cabecera", t(20, 40, "‹ Volver", 13, ACCENT, 600), t(20, 80, "Créditos", 30, INK, 800))]
p.append(g("resumen", r(16, 96, 328, 132, "#14402F", 22),
           label("Deuda total", 32, 124).replace("#9AA09B", "#CFE9DD"),
           t(32, 152, "Bs. 22.232,55", 22, "#FFFFFF", 800),
           t(32, 174, "$ 50,00", 17, "#FFFFFF", 800),
           t(32, 198, "Próximo pago: Bs. 3.705,43 · 15 nov 2026", 11, "#DDEBE3"),
           t(32, 214, "Bancaribe tdc", 11, "#CFE9DD")))
p.append(g("titulo-tarjetas", label("Tarjetas de crédito", 20, 254)))
p.append(card_block("tarjeta-bancaribe", 16, 264, 328, 76,
                    t(32, 292, "Bancaribe tdc", 15, INK, 600),
                    t(32, 310, "Bancaribe · vence 15 nov · 90 % usado", 11, MUTED),
                    t(328, 292, "Bs. 22.232,55", 14, NEG, 800, "end"),
                    t(328, 310, "Disp. Bs. 2.467,45", 11, MUTED, 400, "end")))
p.append(g("accion-cashea", t(20, 366, "+ Compra de Cashea (inicial + cuotas)", 13, ACCENT, 700)))
p.append(g("titulo-cuotas", label("Cuotas", 20, 396)))
p.append(card_block("cuota-cashea", 16, 406, 328, 92,
                    t(32, 434, "Cashea · Principal", 15, INK, 600),
                    t(32, 452, "vence 24 nov 2026 · cuota $ 16,66", 11, MUTED),
                    t(32, 468, "0 de 3 cuotas pagadas", 11, MUTED),
                    t(328, 434, "$ 50,00", 14, NEG, 800, "end"),
                    r(32, 478, 296, 6, "#242825", 3)))
p.append(g("nota", t(20, 522, "Cashea se paga cada 15 días: la deuda es la suma", 10.5, MUTED),
           t(20, 538, "de las cuotas pendientes.", 10.5, MUTED)))
p.append(nav("Menú"))
open("svg/creditos.svg", "w").write(frame("creditos", "".join(p)))

# ---------- 5. MENÚ ----------
p = [big_title(20, 64, "Menú")]
p.append(g("grupo-creditos", label("Créditos", 20, 100),
           list_row("fila-clasificar", 16, 110, 328, "Clasificar historial", "Sugiere categoría", "›"),
           list_row("fila-deudas", 16, 166, 328, "Créditos y deudas", "Tarjetas, Cashea y cuotas", "›")))
p.append(g("grupo-exchange", label("Exchange", 20, 244),
           list_row("fila-binance", 16, 254, 328, "Binance", "Saldos, depósitos, P2P y Pay", "›")))
p.append(g("grupo-app", label("Aplicación", 20, 332),
           list_row("fila-modelo", 16, 342, 328, "Modelo de lenguaje", "Nano o modelo local", "›"),
           list_row("fila-version", 16, 398, 328, "Versión 0.2.4", "Última versión", "›")))
p.append(nav("Menú"))
open("svg/menu.svg", "w").write(frame("menu", "".join(p)))

# ---------- 6. NUEVO REGISTRO ----------
p = [g("barra", t(24, 36, "Cancelar", 13, ACCENT, 600), t(180, 36, "Nuevo registro", 14, INK, 700, "middle"),
        t(336, 36, "Guardar", 13, ACCENT, 700, "end"))]
p.append(g("describir", r(16, 52, 328, 44, TILE, 14, LINE),
           t(32, 79, "Describir (ej. gasté 20 en taxi ayer)", 12, MUTED),
           t(300, 116, "Interpretar", 12, ACCENT, 700, "middle")))
p.append(g("tipo", r(16, 132, 328, 40, TILE, 20), r(20, 136, 160, 32, ACCENT, 16),
           t(100, 157, "Gasto", 12, AINK, 700, "middle"), t(260, 157, "Ingreso", 12, MUTED, 500, "middle")))
p.append(g("monto", t(180, 230, "Bs. 0,00", 40, INK, 800, "middle")))
p.append(g("cuenta", r(16, 260, 328, 56, TILE, 14), t(32, 284, "Bancaribe", 14, INK),
           t(328, 284, "VES", 11, MUTED, 400, "end"), t(32, 302, "Cuenta", 10, MUTED)))
p.append(g("categoria", r(16, 324, 328, 56, TILE, 14), t(32, 348, "Sin categoría", 14, INK),
           t(328, 348, "Opcional", 11, MUTED, 400, "end"), t(32, 366, "Categoría", 10, MUTED)))
p.append(g("fecha", r(16, 388, 100, 36, TILE, 18), r(124, 388, 100, 36, ACCENT, 18), r(232, 388, 112, 36, TILE, 18),
           t(66, 411, "Hoy", 11, INK, 600, "middle"), t(174, 411, "Ayer", 11, AINK, 700, "middle"),
           t(288, 411, "Otra fecha", 11, INK, 600, "middle")))
keys = ["1","2","3","4","5","6","7","8","9",",","0","⌫"]
keypad = []
for i, k in enumerate(keys):
    x = 20 + (i % 3) * 110; y = 470 + (i // 3) * 64
    keypad += [r(x, y, 100, 54, TILE, 14), t(x + 50, y + 36, k, 20, INK, 600, "middle")]
p.append(g("teclado", *keypad))
open("svg/nuevo-registro.svg", "w").write(frame("nuevo-registro", "".join(p)))

# ---------- 7. REVISIÓN ----------
p = [g("cabecera", t(20, 40, "‹ Volver", 13, ACCENT, 600), t(20, 80, "Revisión", 30, INK, 800))]
p.append(card_block("estado-ia", 16, 96, 328, 76,
                    t(32, 122, "✦  Gemini Nano (AICore)", 13, ACCENT, 700),
                    t(32, 142, "Listo para leer capturas en este teléfono.", 11, MUTED)))
p.append(card_block("lote", 16, 182, 328, 84,
                    t(32, 208, "Analizar pendientes", 14, INK, 600),
                    t(32, 226, "12 capturas sin revisar · descarta avisos y OTP", 11, MUTED),
                    r(32, 236, 120, 24, ACCENT, 12), t(92, 252, "Analizar 12", 11, AINK, 700, "middle")))
p.append(g("tarjeta-captura",
           r(16, 276, 328, 210, CARD, 18, LINE),
           t(32, 304, "Banesco · SMS", 11, MUTED),
           t(32, 328, "Compra por Bs. 1.240,00 en FARMACIA X", 13, INK, 600),
           line(32, 342, 328, 342),
           t(32, 366, "Monto", 11, MUTED), t(328, 366, "Bs. 1.240,00", 12, INK, 700, "end"),
           t(32, 388, "Comercio", 11, MUTED), t(328, 388, "FARMACIA X", 12, INK, 700, "end"),
           t(32, 410, "Categoría", 11, MUTED), t(328, 410, "Salud", 12, ACCENT, 700, "end"),
           r(32, 428, 96, 34, TILE, 12), t(80, 450, "Descartar", 11, INK, 600, "middle"),
           r(200, 428, 128, 34, ACCENT, 12), t(264, 450, "Usar datos", 11, AINK, 700, "middle")))
p.append(nav("Menú"))
open("svg/revision.svg", "w").write(frame("revision", "".join(p)))

# ---------- 8. AJUSTES ----------
p = [big_title(20, 64, "Ajustes")]
p.append(g("apariencia", label("Apariencia", 20, 100),
           card_block("tema", 16, 110, 328, 96,
                      t(32, 136, "Tema", 12, MUTED),
                      r(32, 146, 88, 32, TILE, 12), r(126, 146, 88, 32, ACCENT, 12), r(220, 146, 88, 32, TILE, 12),
                      t(76, 167, "Sistema", 11, MUTED, 500, "middle"),
                      t(170, 167, "Claro", 11, AINK, 700, "middle"),
                      t(264, 167, "Oscuro", 11, MUTED, 500, "middle"))))
p.append(g("captura", label("Captura automática", 20, 236),
           list_row("fila-notificaciones", 16, 246, 328, "Acceso a notificaciones", "Activado", "›"),
           list_row("fila-avisos", 16, 298, 328, "Notificaciones de la app", "Activado", "›"),
           list_row("fila-revision", 16, 350, 328, "Revisión de capturas", "Lo que el parser no pudo", "›")))
p.append(g("datos", label("Datos y conexiones", 20, 428),
           list_row("fila-tasas", 16, 438, 328, "Tasa de cambio", "BCV y Binance", "›"),
           list_row("fila-portales", 16, 490, 328, "Portales de banco", "Leer movimientos", "›")))
p.append(nav("Menú"))
open("svg/ajustes.svg", "w").write(frame("ajustes", "".join(p)))

print("ok")
