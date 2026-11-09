# Genera las pantallas de TuBolsillo como SVG editables (una capa por elemento).
W, H = 360, 780
BG, CARD, TILE, LINE = "#0E1110", "#171A18", "#1F2320", "#2A2F2B"
INK, MUTED, ACCENT, AINK, NEG = "#E9ECEA", "#9AA09B", "#5FD6A6", "#04241A", "#F2796F"
FONT = "Inter, 'Segoe UI', Roboto, system-ui, sans-serif"

def t(x, y, s, size=13, fill=INK, weight=400, anchor="start", ls=None):
    extra = f' letter-spacing="{ls}"' if ls else ""
    s = s.replace("&", "&amp;").replace("<", "&lt;")
    return (f'<text x="{x}" y="{y}" font-family="{FONT}" font-size="{size}" fill="{fill}" '
            f'font-weight="{weight}" text-anchor="{anchor}"{extra}>{s}</text>')

def rect(x, y, w, h, fill, r=0, stroke=None, sw=1):
    st = f' stroke="{stroke}" stroke-width="{sw}"' if stroke else ""
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}"{st}/>'

def card(x, y, w, h, r=18):
    return rect(x, y, w, h, CARD, r, LINE)

def nav(active):
    out = [rect(0, H-62, W, 62, "#0B0E0D"), f'<line x1="0" y1="{H-62}" x2="{W}" y2="{H-62}" stroke="{LINE}"/>']
    items = [("Inicio", 40), ("Registros", 118), ("Estadísticas", 250), ("Menú", 322)]
    for name, x in items:
        col = ACCENT if name == active else MUTED
        wt = 700 if name == active else 400
        out.append(t(x, H-32, name, 10, col, wt, "middle"))
    out.append(rect(152, H-58, 56, 40, ACCENT, 14))
    out.append(t(180, H-32, "+", 22, AINK, 700, "middle"))
    return "".join(out)

def frame(body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">'
            f'<rect width="{W}" height="{H}" fill="{BG}"/>' + body + "</svg>")

# ---------- INICIO ----------
b = []
b.append(t(20, 64, "Inicio", 30, INK, 800))
b.append(t(20, 84, "TuBolsillo · Finanzas personales", 12, MUTED))
b.append(t(48, 124, "Cuentas", 13, INK, 700))
b.append(rect(40, 132, 62, 2, ACCENT))
b.append(t(148, 124, "Presupuestos", 13, MUTED))
b.append(t(262, 124, "Deudas", 13, MUTED))
b.append(rect(16, 146, 328, 92, "#14402F", 22))
b.append(t(32, 176, "Buenas tardes", 12, "#D8E9DF"))
b.append(t(32, 204, "Tu dinero, claro y en orden.", 21, "#FFFFFF", 800))
b.append(card(16, 250, 328, 92))
b.append(t(32, 278, "TASAS DE HOY", 10, MUTED, 700, ls="1"))
b.append(t(292, 278, "Calculadora", 12, ACCENT, 700))
for i, (k, v) in enumerate([("DÓLAR BCV", "Bs. 893,21"), ("EURO BCV", "Bs. 999,76"), ("USDT P2P", "Bs. 1.042,04")]):
    x = 28 + i * 104
    b.append(rect(x, 290, 96, 40, TILE, 12))
    b.append(t(x + 8, 305, k, 8.5, MUTED, 600))
    b.append(t(x + 8, 321, v, 12, INK, 700))
b.append(card(16, 354, 328, 74))
b.append(t(32, 378, "Tu saldo en cuentas", 11, MUTED))
b.append(t(32, 402, "$ 1.092,43", 24, INK, 800))
b.append(t(32, 418, "≈ Bs. 1.138.371,80", 11, MUTED))
tiles = [("Bancaribe", "VES 12.594,11"), ("Banesco", "VES 15.480,25"), ("Bancaribe tdc", "VES 2.467,45"),
         ("Bnc credit", "VES 114.311,14"), ("Binance USDT", "USDT 4,71"), ("+", "")]
for i, (nm, amt) in enumerate(tiles):
    x = 16 + (i % 3) * 112
    y = 440 + (i // 3) * 92
    b.append(rect(x, y, 104, 84, TILE, 14))
    b.append(t(x + 10, y + 34, nm, 10.5, INK, 600))
    b.append(t(x + 10, y + 66, amt, 11.5, INK, 800))
b.append(nav("Inicio"))
open("svg/inicio.svg", "w").write(frame("".join(b)))

# ---------- CRÉDITOS ----------
b = []
b.append(t(20, 40, "‹ Volver", 13, ACCENT, 600))
b.append(t(20, 80, "Créditos", 30, INK, 800))
b.append(rect(16, 96, 328, 132, "#14402F", 22))
b.append(t(32, 124, "DEUDA TOTAL", 10, "#CFE9DD", 700, ls="1"))
b.append(t(32, 152, "Bs. 22.232,55", 22, "#FFFFFF", 800))
b.append(t(32, 174, "$ 50,00", 17, "#FFFFFF", 800))
b.append(t(32, 198, "Próximo pago: Bs. 3.705,43 · 15 nov 2026", 11, "#DDEBE3"))
b.append(t(32, 214, "Bancaribe tdc", 11, "#CFE9DD"))
b.append(t(20, 254, "TARJETAS DE CRÉDITO", 10, MUTED, 700, ls="1"))
b.append(card(16, 264, 328, 76))
b.append(t(32, 292, "Bancaribe tdc", 15, INK, 600))
b.append(t(32, 310, "Bancaribe · vence 15 nov · 90 % usado", 11, MUTED))
b.append(t(328, 292, "Bs. 22.232,55", 14, NEG, 800, "end"))
b.append(t(328, 310, "Disp. Bs. 2.467,45", 11, MUTED, 400, "end"))
b.append(t(20, 366, "+ Compra de Cashea (inicial + cuotas)", 13, ACCENT, 700))
b.append(t(20, 396, "CUOTAS", 10, MUTED, 700, ls="1"))
b.append(card(16, 406, 328, 92))
b.append(t(32, 434, "Cashea · Principal", 15, INK, 600))
b.append(t(32, 452, "vence 24 nov 2026 · cuota $ 16,66", 11, MUTED))
b.append(t(32, 468, "0 de 3 cuotas pagadas", 11, MUTED))
b.append(t(328, 434, "$ 50,00", 14, NEG, 800, "end"))
b.append(rect(32, 478, 296, 6, "#242825", 3))
b.append(t(20, 522, "Cashea se paga cada 15 días: la deuda es la suma", 10.5, MUTED))
b.append(t(20, 538, "de las cuotas pendientes.", 10.5, MUTED))
b.append(nav("Menú"))
open("svg/creditos.svg", "w").write(frame("".join(b)))

# ---------- MENÚ ----------
b = []
b.append(t(20, 64, "Menú", 30, INK, 800))
b.append(t(20, 100, "CRÉDITOS", 10, MUTED, 700, ls="1"))
b.append(card(16, 110, 328, 104, 16))
b.append(t(32, 142, "Clasificar historial", 14, INK))
b.append(t(328, 142, "Sugiere categoría", 11, MUTED, 400, "end"))
b.append(f'<line x1="32" y1="158" x2="328" y2="158" stroke="{LINE}"/>')
b.append(t(32, 188, "Créditos y deudas", 14, INK))
b.append(t(328, 188, "Tarjetas, Cashea y cuotas", 11, MUTED, 400, "end"))
b.append(t(20, 244, "APLICACIÓN", 10, MUTED, 700, ls="1"))
b.append(card(16, 254, 328, 104, 16))
b.append(t(32, 286, "Modelo de lenguaje", 14, INK))
b.append(t(328, 286, "Nano o modelo local", 11, MUTED, 400, "end"))
b.append(f'<line x1="32" y1="302" x2="328" y2="302" stroke="{LINE}"/>')
b.append(t(32, 332, "Versión 0.2.4", 14, ACCENT, 600))
b.append(t(328, 332, "Última versión", 11, MUTED, 400, "end"))
b.append(nav("Menú"))
open("svg/menu.svg", "w").write(frame("".join(b)))
print("ok")
