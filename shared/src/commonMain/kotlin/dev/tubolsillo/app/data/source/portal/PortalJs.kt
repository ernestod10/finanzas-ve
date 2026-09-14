package dev.tubolsillo.app.data.source.portal

/**
 * Construye el JavaScript que corre dentro del WebView con la sesión viva.
 * Todo aquí es puro (String -> String) para poder testearlo sin dispositivo.
 *
 * Reglas duras aprendidas:
 * - Nunca `form.submit()`: salta el handler `onsubmit`, que es donde varios
 *   bancos cifran la clave. Se hace `.click()` en el botón real.
 * - Nunca asignar `.value` a secas: en formularios React/Angular el estado del
 *   framework no se entera y el POST viaja vacío. Se usa el setter nativo y se
 *   despachan `input` y `change`.
 * - Nunca fallar en silencio: cualquier excepción vuelve como status "error".
 */
object PortalJs {

    const val VERSION = 1

    /** Escapa un texto para incrustarlo entre comillas simples en JS. */
    fun escapeJsString(raw: String): String {
        val sb = StringBuilder(raw.length + 8)
        for (ch in raw) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '\'' -> sb.append("\\'")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\u2028' -> sb.append("\\u2028")
                '\u2029' -> sb.append("\\u2029")
                '<' -> sb.append("\\u003C")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * `evaluateJavascript` devuelve el resultado como literal JSON: una cadena
     * viene entre comillas y con escapes. Esto la desenvuelve.
     */
    fun unwrapResult(evaluateJavascriptResult: String?): String? {
        val raw = evaluateJavascriptResult?.trim() ?: return null
        if (raw.isEmpty() || raw == "null" || raw == "undefined") return null
        if (raw.length < 2 || raw.first() != '"' || raw.last() != '"') return raw
        val body = raw.substring(1, raw.length - 1)
        val sb = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c != '\\' || i == body.length - 1) {
                sb.append(c); i++; continue
            }
            when (val next = body[i + 1]) {
                'n' -> { sb.append('\n'); i += 2 }
                'r' -> { sb.append('\r'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                'b' -> { sb.append('\b'); i += 2 }
                '"' -> { sb.append('"'); i += 2 }
                '\\' -> { sb.append('\\'); i += 2 }
                '/' -> { sb.append('/'); i += 2 }
                'u' -> {
                    val hex = body.substring(i + 2, minOf(i + 6, body.length))
                    val code = hex.toIntOrNull(16)
                    if (code != null && hex.length == 4) { sb.append(code.toChar()); i += 6 }
                    else { sb.append(next); i += 2 }
                }
                else -> { sb.append(next); i += 2 }
            }
        }
        return sb.toString()
    }

    /** Sondeo de estado: login | ready | unknown. En SPAs no sirve onPageFinished. */
    fun buildProbeScript(recipe: PortalRecipe): String = """
        (function(){
          try {
            var login = ${recipe.loginFormMarkerJs};
            var inside = ${recipe.loggedInMarkerJs};
            return JSON.stringify({ state: login ? 'login' : (inside ? 'ready' : 'unknown'), url: location.href });
          } catch (e) {
            return JSON.stringify({ state: 'unknown', message: String(e) });
          }
        })();
    """.trimIndent()

    /** Rellena y envía el formulario de login. Solo se invoca desde un botón. */
    fun buildLoginScript(recipe: PortalRecipe, user: String, password: String): String {
        val userSel = recipe.userSelector?.let { "'" + escapeJsString(it) + "'" } ?: "null"
        val passSel = recipe.passSelector?.let { "'" + escapeJsString(it) + "'" } ?: "null"
        val submitSel = recipe.submitSelector?.let { "'" + escapeJsString(it) + "'" } ?: "null"
        return """
            (function(){
              try {
                function setValue(el, value){
                  var proto = window.HTMLInputElement.prototype;
                  var setter = Object.getOwnPropertyDescriptor(proto, 'value');
                  if (setter && setter.set) { setter.set.call(el, value); } else { el.value = value; }
                  el.dispatchEvent(new Event('input', { bubbles: true }));
                  el.dispatchEvent(new Event('change', { bubbles: true }));
                }
                var passEl = ${passSel} ? document.querySelector(${passSel})
                                        : document.querySelector('input[type=password]');
                if (!passEl) { return JSON.stringify({ status: 'error', message: 'sin campo de clave' }); }
                var userEl = ${userSel} ? document.querySelector(${userSel}) : null;
                if (!userEl) {
                  var form = passEl.form || document;
                  var cands = form.querySelectorAll('input[type=text],input[type=email],input[type=tel],input:not([type])');
                  for (var i = 0; i < cands.length; i++) {
                    var c = cands[i];
                    if (c.offsetParent === null) continue;
                    var meta = ((c.name||'') + ' ' + (c.id||'') + ' ' + (c.getAttribute('autocomplete')||'')).toLowerCase();
                    if (meta.indexOf('search') >= 0 || meta.indexOf('buscar') >= 0) continue;
                    userEl = c;
                    if (meta.indexOf('user') >= 0 || meta.indexOf('usuario') >= 0 || meta.indexOf('login') >= 0) break;
                  }
                }
                if (!userEl) { return JSON.stringify({ status: 'error', message: 'sin campo de usuario' }); }
                setValue(userEl, '${escapeJsString(user)}');
                setValue(passEl, '${escapeJsString(password)}');
                var btn = ${submitSel} ? document.querySelector(${submitSel}) : null;
                if (!btn) {
                  var f = passEl.form;
                  if (f) { btn = f.querySelector('button[type=submit],input[type=submit],button:not([type])'); }
                }
                if (btn) { btn.click(); return JSON.stringify({ status: 'ok', message: 'submit' }); }
                if (passEl.form) { passEl.form.submit(); return JSON.stringify({ status: 'ok', message: 'form-fallback' }); }
                return JSON.stringify({ status: 'error', message: 'sin boton de envio' });
              } catch (e) {
                return JSON.stringify({ status: 'error', message: String(e) });
              }
            })();
        """.trimIndent()
    }

    /** Extrae las filas de movimientos y el saldo. Devuelve un [PortalPayload]. */
    fun buildExtractScript(recipe: PortalRecipe): String {
        fun sel(value: String?) = value?.let { "'" + escapeJsString(it) + "'" } ?: "null"
        return """
            (function(){
              try {
                function txt(el){ return ((el && (el.innerText || el.textContent)) || '').replace(/\s+/g,' ').trim(); }
                function pick(row, selector){ if (!selector) return ''; var el = row.querySelector(selector); return el ? txt(el) : ''; }
                var AMT = /-?\d{1,3}(?:[.,]\d{3})*[.,]\d{2}/;
                var DATE = /\b(\d{1,2}[\/\-.]\d{1,2}[\/\-.]\d{2,4}|\d{4}-\d{2}-\d{2})\b/;
                if (${recipe.loginFormMarkerJs}) { return JSON.stringify({ status: 'login', url: location.href }); }
                var out = { status: 'ok', url: location.href, balanceText: null, rows: [] };
                var nodes = document.querySelectorAll('${escapeJsString(recipe.rowsSelector)}');
                var maxRows = ${recipe.maxRows};
                for (var i = 0; i < nodes.length && out.rows.length < maxRows; i++) {
                  var node = nodes[i];
                  var whole = txt(node);
                  if (!whole) continue;
                  var row = {
                    date: pick(node, ${sel(recipe.cellDateSelector)}),
                    description: pick(node, ${sel(recipe.cellDescriptionSelector)}),
                    amount: pick(node, ${sel(recipe.cellAmountSelector)}),
                    debit: pick(node, ${sel(recipe.cellDebitSelector)}),
                    credit: pick(node, ${sel(recipe.cellCreditSelector)}),
                    reference: pick(node, ${sel(recipe.cellReferenceSelector)}),
                    balance: '',
                    raw: whole
                  };
                  if (!row.date || (!row.amount && !row.debit && !row.credit)) {
                    var cells = node.querySelectorAll('td,th');
                    if (cells.length < 2) continue;
                    var texts = [];
                    for (var c = 0; c < cells.length; c++) texts.push(txt(cells[c]));
                    var joined = texts.join(' ');
                    if (!DATE.test(joined) || !AMT.test(joined)) continue;
                    if (!row.date) { row.date = joined.match(DATE)[0]; }
                    if (!row.amount && !row.debit && !row.credit) {
                      for (var a = texts.length - 1; a >= 0; a--) {
                        if (AMT.test(texts[a]) && !DATE.test(texts[a])) { row.amount = texts[a]; break; }
                      }
                    }
                    if (!row.description) {
                      var best = '', len = 0;
                      for (var d = 0; d < texts.length; d++) {
                        var v = texts[d];
                        if (v === row.amount || DATE.test(v) || v.length <= len) continue;
                        len = v.length; best = v;
                      }
                      row.description = best;
                    }
                  }
                  if (!row.date) continue;
                  if (!row.amount && !row.debit && !row.credit) continue;
                  out.rows.push(row);
                }
                var balSel = ${sel(recipe.balanceSelector)};
                if (balSel) {
                  var balEl = document.querySelector(balSel);
                  if (balEl) out.balanceText = txt(balEl);
                }
                if (!out.balanceText) {
                  var body = txt(document.body).slice(0, 20000);
                  var bm = body.match(/saldo[^0-9-]{0,40}(-?\d{1,3}(?:[.,]\d{3})*[.,]\d{2})/i);
                  if (bm) out.balanceText = bm[1];
                }
                if (out.rows.length === 0) { out.status = 'empty'; }
                return JSON.stringify(out);
              } catch (e) {
                return JSON.stringify({ status: 'error', message: String(e) });
              }
            })();
        """.trimIndent()
    }
}
