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

    private const val ALL_DOCS_JS = """
        function allDocs(){
          var out = [document];
          function collect(win){
            try {
              var frames = win.frames;
              for (var i = 0; i < frames.length; i++) {
                try {
                  var d = frames[i].document;
                  if (d && out.indexOf(d) < 0) {
                    out.push(d);
                    collect(frames[i]);
                  }
                } catch (_e) {}
              }
            } catch (_e) {}
          }
          collect(window);
          try {
            var iframes = document.querySelectorAll('iframe, frame');
            for (var j = 0; j < iframes.length; j++) {
              try {
                var idoc = iframes[j].contentDocument || (iframes[j].contentWindow && iframes[j].contentWindow.document);
                if (idoc && out.indexOf(idoc) < 0) out.push(idoc);
              } catch (_e) {}
            }
          } catch (_e) {}
          return out;
        }
        function visible(el){
          if (!el) return false;
          var style = null;
          try {
            var win = (el.ownerDocument && el.ownerDocument.defaultView) || window;
            style = win.getComputedStyle(el);
          } catch (_e) {}
          if (style && (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0')) return false;
          return !!(el.offsetParent !== null || el.getClientRects().length > 0);
        }
    """

    /** Sondeo de estado: login | ready | error_page | unknown. Soporta iframes (BanescOnline). */
    fun buildProbeScript(recipe: PortalRecipe): String = """
        (function(){
          try {
            $ALL_DOCS_JS
            var docs = allDocs();
            for (var d = 0; d < docs.length; d++) {
              var doc = docs[d];
              try {
                if (doc.defaultView && typeof doc.defaultView.isDisabledButton !== 'undefined') {
                  doc.defaultView.isDisabledButton = false;
                }
              } catch (_e) {}
              var disBtns = doc.querySelectorAll('button.btn[disabled], a.btn[disabled]');
              for (var b = 0; b < disBtns.length; b++) {
                disBtns[b].disabled = false;
                disBtns[b].removeAttribute('disabled');
                if (disBtns[b].classList) disBtns[b].classList.add('activado-btn');
              }
              if (doc.querySelector('#container-error, #errorpage')) {
                return JSON.stringify({ state: 'error_page', url: location.href });
              }
              // Sesión del banco cerrada (BNC: "Sesión finalizada" + "Volver a ingresar").
              var bodyText = ((doc.body && doc.body.innerText) || '');
              if (/sesi[oó]n\s+(finalizada|expirada|terminada|cerrada)|volver\s+a\s+ingresar|reingresar|iniciar\s+sesi[oó]n\s+nuevamente|su\s+sesi[oó]n\s+ha\s+(expirado|finalizado)/i.test(bodyText)
                  && !doc.querySelector('input[type=password]')) {
                return JSON.stringify({ state: 'expired', url: location.href });
              }
            }
            var hasLogin = !!(${recipe.loginFormMarkerJs});
            var loginStep = null;
            for (var i = 0; i < docs.length; i++) {
              var passEl = docs[i].querySelector('input[type=password], input#txtClave');
              if (passEl && visible(passEl)) {
                hasLogin = true;
                loginStep = 'pass';
                break;
              }
              var userEl = docs[i].querySelector('input#txtUsuario, input#userlogin, input#UserName, input[name=UserId], input[name=txtUsuario], input#CardNumber');
              if (userEl && visible(userEl)) {
                hasLogin = true;
                loginStep = 'user';
              }
            }
            if (hasLogin) {
              return JSON.stringify({ state: 'login', step: loginStep || 'form', url: location.href });
            }
            var inside = !!(${recipe.loggedInMarkerJs});
            if (!inside) {
              for (var j = 0; j < docs.length; j++) {
                var bodyTxt = ((docs[j].body && docs[j].body.innerText) || '');
                if (docs[j].querySelectorAll('table tr, mat-row, [role=row]').length > 3 ||
                    /saldo|disponible|directorio\s+de\s+productos|posici[oó]n\s+consolidada|estado\s+de\s+cuenta|movimientos|\b0134\d{4}|\b0114\d{4}/i.test(bodyTxt)) {
                  inside = true;
                  break;
                }
              }
            }
            return JSON.stringify({ state: inside ? 'ready' : 'unknown', url: location.href });
          } catch (e) {
            return JSON.stringify({ state: 'unknown', message: String(e) });
          }
        })();
    """.trimIndent()

    /** Rellena y envía el formulario de login (1 paso o 2 pasos, incluso dentro de iframes como BanescOnline). */
    fun buildLoginScript(recipe: PortalRecipe, user: String, password: String): String {
        val userSel = recipe.userSelector?.let { "'" + escapeJsString(it) + "'" } ?: "null"
        val passSel = recipe.passSelector?.let { "'" + escapeJsString(it) + "'" } ?: "null"
        val submitSel = recipe.submitSelector?.let { "'" + escapeJsString(it) + "'" } ?: "null"
        val idSel = recipe.idSelector?.let { "'" + escapeJsString(it) + "'" } ?: "null"
        val userPart = if (recipe.idSelector != null) user.substringBefore('|').trim() else user
        val idPart = if (recipe.idSelector != null) user.substringAfter('|', "").trim() else ""
        return """
            (function(){
              try {
                $ALL_DOCS_JS
                function setValue(el, value){
                  var win = (el.ownerDocument && el.ownerDocument.defaultView) || window;
                  el.focus();
                  var proto = win.HTMLInputElement ? win.HTMLInputElement.prototype : window.HTMLInputElement.prototype;
                  var setter = Object.getOwnPropertyDescriptor(proto, 'value');
                  if (setter && setter.set) { setter.set.call(el, value); } else { el.value = value; }
                  el.dispatchEvent(new Event('input', { bubbles: true }));
                  el.dispatchEvent(new Event('change', { bubbles: true }));
                  el.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
                  el.blur();
                }
                var docs = allDocs();
                var passEl = null;
                var userEl = null;
                var targetDoc = document;
                for (var d = 0; d < docs.length; d++) {
                  var doc = docs[d];
                  if (!passEl) {
                    var p = ${passSel} ? doc.querySelector(${passSel}) : doc.querySelector('input[type=password]');
                    if (p && visible(p)) { passEl = p; targetDoc = doc; }
                  }
                  if (!userEl && ${userSel}) {
                    var u = doc.querySelector(${userSel});
                    if (u && visible(u)) { userEl = u; targetDoc = doc; }
                  }
                }
                if (!userEl) {
                  for (var d2 = 0; d2 < docs.length && !userEl; d2++) {
                    var doc2 = docs[d2];
                    var root = (passEl && passEl.ownerDocument === doc2 && passEl.form) || doc2;
                    var cands = root.querySelectorAll('input[type=text],input[type=email],input[type=tel],input:not([type])');
                    for (var i = 0; i < cands.length; i++) {
                      var c = cands[i];
                      if (!visible(c)) continue;
                      var meta = ((c.name||'') + ' ' + (c.id||'') + ' ' + (c.placeholder||'') + ' ' + (c.getAttribute('formcontrolname')||'') + ' ' + (c.getAttribute('autocomplete')||'')).toLowerCase();
                      if (meta.indexOf('search') >= 0 || meta.indexOf('buscar') >= 0 || meta.indexOf('token') >= 0 || meta.indexOf('otp') >= 0 || meta.indexOf('respuesta') >= 0) continue;
                      if (meta.indexOf('user') >= 0 || meta.indexOf('usuario') >= 0 || meta.indexOf('login') >= 0 || meta.indexOf('card') >= 0) {
                        userEl = c;
                        targetDoc = doc2;
                        break;
                      }
                    }
                  }
                }
                if (!userEl && !passEl) {
                  return JSON.stringify({ status: 'error', message: 'sin campos de login visibles' });
                }
                // Bancos con paso de clave separado (BNC): en el paso 2 solo se llena la clave.
                if (passEl && ${idSel}) { userEl = null; }
                if (userEl) setValue(userEl, '${escapeJsString(userPart)}');
                if (userEl && ${idSel}) {
                  var idDoc = userEl.ownerDocument || document;
                  var idEl = idDoc.querySelector(${idSel});
                  if (idEl && visible(idEl)) setValue(idEl, '${escapeJsString(idPart)}');
                }
                if (passEl) setValue(passEl, '${escapeJsString(password)}');
                var anchor = passEl || userEl;
                var ownerDoc = (anchor && anchor.ownerDocument) || targetDoc || document;
                var btn = ${submitSel} ? ownerDoc.querySelector(${submitSel}) : null;
                if (!btn || !visible(btn)) {
                  var f = (anchor && anchor.form) || ownerDoc;
                  var btns = f.querySelectorAll('input[type=submit],button[type=submit],input.LogButton,button.btn-primary,button.mat-flat-button,button.mat-raised-button,button:not([type])');
                  for (var b = 0; b < btns.length; b++) {
                    if (!visible(btns[b])) continue;
                    var bTxt = ((btns[b].value || btns[b].innerText || btns[b].id || '')).toLowerCase();
                    if (/cancelar|limpiar|salir|olvid|registr/i.test(bTxt)) continue;
                    btn = btns[b];
                    break;
                  }
                }
                var stepStatus = (userEl && !passEl) ? 'step1' : 'ok';
                if (btn) {
                  btn.disabled = false;
                  btn.removeAttribute('disabled');
                  btn.click();
                  return JSON.stringify({ status: stepStatus, message: 'submit' });
                }
                if (anchor && anchor.form) {
                  anchor.form.submit();
                  return JSON.stringify({ status: stepStatus, message: 'form-fallback' });
                }
                return JSON.stringify({ status: 'error', message: 'sin boton de envio' });
              } catch (e) {
                return JSON.stringify({ status: 'error', message: String(e) });
              }
            })();
        """.trimIndent()
    }

    /**
     * Navegación automática post-login: cuando el portal aterriza en Posición Consolidada
     * o Directorio de Productos (BanescOnline / Bancaribe) sin filas de movimientos aún:
     * 0) Si hay un modal post-login de BanescOnline ("Más Tarde" / "Continuar" / #bAceptar2), lo cierra.
     * 1) Si hay un filtro con <select> de cuenta y botón "Consultar"/"Buscar"/"Aceptar", lo envía.
     * 2) En Posición Consolidada / Directorio de Productos, hace clic en la cuenta (0134 / 0114 / Corriente / Ahorro / Verde) para abrir la vista de movimientos.
     * 3) Si hay secciones colapsadas (.collapsible-div / .collapsible-header), las despliega.
     * 4) Si hay menú lateral/superior con "Movimientos"/"Estado de cuenta"/"Consultas", lo abre.
     */
    fun buildAutoNavigateScript(recipe: PortalRecipe): String = """
        (function(){
          try {
            $ALL_DOCS_JS
            function txt(el){ return ((el && (el.innerText || el.textContent)) || '').replace(/\s+/g,' ').trim(); }
            var docs = allDocs();
            for (var d0 = 0; d0 < docs.length; d0++) {
              var doc0 = docs[d0];
              try {
                if (doc0.defaultView && typeof doc0.defaultView.isDisabledButton !== 'undefined') {
                  doc0.defaultView.isDisabledButton = false;
                }
              } catch (_e) {}
              var allBtns = doc0.querySelectorAll('button.btn[disabled], a.btn[disabled], button[disabled]');
              for (var b = 0; b < allBtns.length; b++) {
                allBtns[b].disabled = false;
                allBtns[b].removeAttribute('disabled');
                if (allBtns[b].classList) allBtns[b].classList.add('activado-btn');
              }

              // 0.a Sesión finalizada: pulsar "Volver a ingresar" / "Reingresar"
              var reloginBtns = doc0.querySelectorAll('button, a, input[type=button], input[type=submit], [onclick]');
              for (var rl = 0; rl < reloginBtns.length; rl++) {
                var rb = reloginBtns[rl];
                if (!visible(rb) || rb.getAttribute('data-tb-nav')) continue;
                var rlTxt = (txt(rb) || rb.value || '').toLowerCase();
                if (/volver\s+a\s+ingresar|reingresar|ingresar\s+nuevamente|iniciar\s+sesi[oó]n|volver\s+al\s+login/.test(rlTxt)) {
                  rb.setAttribute('data-tb-nav', '1');
                  rb.disabled = false;
                  rb.removeAttribute('disabled');
                  rb.click();
                  return JSON.stringify({ status: 'navigated', step: 'relogin', target: rlTxt.slice(0, 40) });
                }
              }

              // 0. Cerrar modales informativos post-login de BanescOnline (SweetAlert "Más Tarde" / "Continuar")
              var swalBtns = doc0.querySelectorAll('.swal2-cancel, .swal2-confirm, .sa-confirm-button-container button, .sweet-alert button');
              for (var sw = 0; sw < swalBtns.length; sw++) {
                var sb = swalBtns[sw];
                if (!visible(sb) || sb.getAttribute('data-tb-nav')) continue;
                var sbTxt = txt(sb).toLowerCase();
                if (/m[aá]s\s+tarde|continuar|aceptar|cerrar|omitir/i.test(sbTxt)) {
                  sb.setAttribute('data-tb-nav', '1');
                  sb.click();
                  return JSON.stringify({ status: 'navigated', step: 'dismiss_modal', target: sbTxt });
                }
              }
            }

            // 1. Si estamos en una vista con <select> de cuenta no seleccionado, elegir la primera cuenta válida.
            var selectedAccountInFilter = false;
            for (var d1 = 0; d1 < docs.length; d1++) {
              var selects = docs[d1].querySelectorAll('select');
              for (var s = 0; s < selects.length; s++) {
                var sel = selects[s];
                if (!visible(sel) || sel.getAttribute('data-tb-selected')) continue;
                if (sel.options && sel.options.length > 1 && sel.selectedIndex <= 0) {
                  var firstOptText = (sel.options[0].text || '').toLowerCase();
                  if (!sel.value || firstOptText.indexOf('seleccione') >= 0 || firstOptText.indexOf('todas') < 0) {
                    // Preferir una opción que contenga 0134 o 0114 o Cuenta
                    var targetIdx = 1;
                    for (var oi = 1; oi < sel.options.length; oi++) {
                      var oTxt = (sel.options[oi].text || '').toLowerCase();
                      if (/0134|0114|corriente|ahorro|verde/i.test(oTxt) && !/visa|mastercard|tdc/i.test(oTxt)) {
                        targetIdx = oi;
                        break;
                      }
                    }
                    sel.selectedIndex = targetIdx;
                    sel.setAttribute('data-tb-selected', '1');
                    selectedAccountInFilter = true;
                    sel.dispatchEvent(new Event('change', { bubbles: true }));
                    var win = docs[d1].defaultView || window;
                    if (typeof win.${'$'} === 'function') { try { win.${'$'}(sel).trigger('change'); } catch(_e){} }
                  }
                }
              }
            }
            if (selectedAccountInFilter) {
              return JSON.stringify({ status: 'navigated', step: 'select_account', target: 'select' });
            }

            // 2. Si hay un botón "Consultar" / "Buscar" / "Ver movimientos" visible en un formulario de consulta, pulsarlo.
            for (var d2 = 0; d2 < docs.length; d2++) {
              var doc2 = docs[d2];
              var firstInput = doc2.querySelector('#main input:not([type=hidden]), #form input:not([type=hidden]), form input:not([type=hidden])');
              if (firstInput && visible(firstInput) && firstInput.type !== 'password' && firstInput.id !== 'txtUsuario') {
                try { firstInput.focus(); firstInput.dispatchEvent(new Event('focus', { bubbles: true })); } catch(_e){}
              }
              var actionBtns = doc2.querySelectorAll('button.btn, a.btn, button[type=submit], input[type=submit], input[type=button], button');
              for (var i = 0; i < actionBtns.length; i++) {
                var ab = actionBtns[i];
                if (!visible(ab) || ab.getAttribute('data-tb-nav')) continue;
                var label = (txt(ab) || ab.value || ab.getAttribute('title') || '').trim().toLowerCase();
                var onclickAttr = (ab.getAttribute('onclick') || '').toLowerCase();
                if (/cancelar|regresar|limpiar|salir|cerrar|volver|exit|logout|exportar|imprimir|pdf|excel/.test(label + ' ' + onclickAttr)) continue;
                if (/^(consultar|buscar|ver movimientos|movimientos|detalle)$/.test(label) ||
                    /\b(consultar|ver movimientos|buscar movimientos)\b/.test(label)) {
                  ab.setAttribute('data-tb-nav', '1');
                  ab.disabled = false;
                  ab.removeAttribute('disabled');
                  ab.click();
                  return JSON.stringify({ status: 'navigated', step: 'submit_filter', target: label });
                }
              }
            }

            // 3. En Directorio de Productos (BanescOnline) o Posición Consolidada (Bancaribe):
            // buscar clic directo en la cuenta (0134... / 0114... / Cuenta Corriente / Cuenta de Ahorro / Cuenta Verde) para abrir movimientos.
            var bestAccountEl = null;
            var bestScore = 0;
            for (var d3 = 0; d3 < docs.length; d3++) {
              var clickables = docs[d3].querySelectorAll('a[href], [onclick], tr, .collection-item, .collapsible-div, .card-panel, .card, [role=button]');
              for (var c = 0; c < clickables.length; c++) {
                var el = clickables[c];
                if (!visible(el) || el.getAttribute('data-tb-nav')) continue;
                var oc = (el.getAttribute('onclick') || '').toLowerCase();
                var href = (el.getAttribute('href') || '').toLowerCase();
                var idName = ((el.id || '') + ' ' + (el.getAttribute('name') || '')).toLowerCase();
                var content = txt(el);
                var lower = content.toLowerCase();
                if (/exit|logout|salir|cerrar|goback|cancelar|limpiar|ayuda|perfil|clave|seguridad|transfer|pago\s+m|recarga|divisa|exportar|imprimir|pdf/i.test(oc + ' ' + href + ' ' + lower)) continue;
                if (/visa|mastercard|paga\s+antes\s+del|pago\s+min|deuda\s+bs|tarjeta\s+de\s+cr[eé]dito/i.test(lower)) continue;
                // Ignorar botones de adorno que sólo retornan false sin navegar
                var ocClean = oc.replace(/\s+/g, '');
                if (el.tagName === 'BUTTON' && (ocClean === 'returnfalse;' || ocClean === 'returnfalse')) continue;
                var score = 0;
                // Enlace directo de número de cuenta Banesco (0134...), Bancaribe (0114...), o BNC (0191...)
                if (/\b0134[\d*•-]{4,20}\b/.test(lower)) {
                  score += 14;
                } else if (/\b0114[\d*•-]{4,20}\b/.test(lower)) {
                  score += 12;
                } else if (/\b0191[\d*•-]{4,20}\b/.test(lower)) {
                  score += 12;
                }
                if (el.tagName === 'A' && (/0134|0114|0191|cuenta|cta\.?|movimiento|detalle|ctacte|ctaaho/i.test(lower + ' ' + idName + ' ' + href))) {
                  score += 8;
                }
                if (href.indexOf('__dopostback') >= 0 && (/cuenta|cta|producto|dg|gv|grid|ctl/i.test(href + ' ' + idName) || /\b(?:0134|0114|0191)/.test(lower))) {
                  score += 9;
                }
                if (oc && /movimiento|detalle|consulta|ctacte|ctaaho|cuenta|redirecttoaction/i.test(oc) &&
                    oc.indexOf('consultaglobal') < 0 && oc.indexOf('openmodal') < 0 && oc.indexOf('opencollapsible') < 0 &&
                    ocClean !== 'returnfalse;' && ocClean !== 'returnfalse') {
                  score += 10;
                }
                if (/(?:cuenta|cta\.?)\s*(?:corriente|de\s+ahorro|ahorro|verde|[uú]nica|bancaribe|banesco|bnc)/i.test(lower)) {
                  score += 8;
                } else if (/\*{3,}\d{3,4}|•{3,}\d{3,4}/.test(lower) && !/4812|5\d{3}/.test(lower)) {
                  score += 5;
                }
                if (content.length > 350) score -= 6;
                if (score > bestScore) {
                  bestScore = score;
                  bestAccountEl = el;
                }
              }
            }
            if (bestAccountEl && bestScore >= 5) {
              var toClick = bestAccountEl;
              if (bestAccountEl.tagName !== 'A' && bestAccountEl.tagName !== 'BUTTON') {
                var innerLinks = bestAccountEl.querySelectorAll('a[href]:not([data-tb-nav]), [onclick]:not([data-tb-nav]), button:not([data-tb-nav])');
                for (var il = 0; il < innerLinks.length; il++) {
                  var cand = innerLinks[il];
                  if (!visible(cand)) continue;
                  var candOc = (cand.getAttribute('onclick') || '').replace(/\s+/g, '').toLowerCase();
                  if (candOc === 'returnfalse;' || candOc === 'returnfalse') continue;
                  var candTxt = (txt(cand) + ' ' + (cand.getAttribute('href') || '') + ' ' + (cand.getAttribute('onclick') || '')).toLowerCase();
                  if (/0134|0114|0191|movimiento|detalle|consulta|cuenta|__dopostback/i.test(candTxt)) {
                    toClick = cand;
                    break;
                  }
                }
              }
              bestAccountEl.setAttribute('data-tb-nav', '1');
              toClick.setAttribute('data-tb-nav', '1');
              toClick.click();
              return JSON.stringify({ status: 'navigated', step: 'open_account', target: (toClick.getAttribute('onclick') || toClick.getAttribute('href') || txt(toClick)).slice(0, 80) });
            }

            // 4. Si hay contenedores colapsados en Posición Consolidada (.collapsible-div o .collapsible-header), abrirlos.
            for (var d4 = 0; d4 < docs.length; d4++) {
              var collapsibles = docs[d4].querySelectorAll('.collapsible-div:not(.active-div), .collapsible-header:not(.active)');
              for (var k = 0; k < collapsibles.length; k++) {
                var col = collapsibles[k];
                if (!visible(col) || col.getAttribute('data-tb-nav')) continue;
                var colTxt = txt(col).toLowerCase();
                if (/tarjeta|credito|crédito|visa|mastercard|prestamo|préstamo|fideicomiso|inversion/i.test(colTxt) && !/cuenta/i.test(colTxt)) continue;
                col.setAttribute('data-tb-nav', '1');
                col.click();
                return JSON.stringify({ status: 'navigated', step: 'expand_collapsible', target: colTxt.slice(0, 50) });
              }
            }

            // 5. Buscar en el menú lateral/superior o tabs un acceso a Consultas / Movimientos / En línea / Estado de cuenta.
            for (var d5 = 0; d5 < docs.length; d5++) {
              var menuLinks = docs[d5].querySelectorAll('#menu-left a, #menu-left [onclick], #footerID a, #footerID [onclick], nav a, .sidenav a, .nav a, ul.tabs a, div[class*=tab] a, a, [onclick], td[onclick], span[onclick]');
              for (var m = 0; m < menuLinks.length; m++) {
                var ml = menuLinks[m];
                if (!visible(ml) || ml.getAttribute('data-tb-nav')) continue;
                var mlOc = (ml.getAttribute('onclick') || '').replace(/\s+/g, '').toLowerCase();
                if (mlOc === 'returnfalse;' || mlOc === 'returnfalse') continue;
                var mlText = (txt(ml) + ' ' + (ml.getAttribute('onclick') || '') + ' ' + (ml.getAttribute('href') || '')).toLowerCase();
                if (/exit|logout|salir|cerrar/.test(mlText)) continue;
                if (/movimiento|estado\s+de\s+cuenta|consulta\s+de\s+cuenta|detalle\s+de\s+cuenta|transactions|en\s+l[ií]nea|25\s+[uú]ltimos|ultimos\s+movimientos/i.test(mlText)) {
                  ml.setAttribute('data-tb-nav', '1');
                  ml.click();
                  return JSON.stringify({ status: 'navigated', step: 'menu_link', target: mlText.slice(0, 60) });
                }
                // Si estamos en Welcome de BNC, abrir Posición con máxima prioridad para capturar todas las cuentas y la TDC
                if (location.href.indexOf('/Welcome') >= 0) {
                  if (/productssummary\/list|posici[oó]n/i.test(mlText)) {
                    ml.setAttribute('data-tb-nav', '1');
                    ml.click();
                    return JSON.stringify({ status: 'navigated', step: 'bnc_welcome_position', target: mlText.slice(0, 60) });
                  }
                }
              }
            }

            // Devolver resumen diagnóstico de los elementos interactivos visibles para depuración.
            var sample = [];
            for (var d6 = 0; d6 < docs.length && sample.length < 10; d6++) {
              var els = docs[d6].querySelectorAll('a[href], [onclick], button, input[type=submit], input[type=button]');
              for (var sIdx = 0; sIdx < els.length && sample.length < 10; sIdx++) {
                if (!visible(els[sIdx])) continue;
                var st = (txt(els[sIdx]) || els[sIdx].value || '').slice(0, 50);
                var soc = (els[sIdx].getAttribute('onclick') || els[sIdx].getAttribute('href') || '').slice(0, 45);
                if (st || soc) sample.push(els[sIdx].tagName + '(' + soc + '):' + st);
              }
            }
            return JSON.stringify({ status: 'idle', url: location.href, sample: sample });
          } catch (e) {
            return JSON.stringify({ status: 'error', message: String(e) });
          }
        })();
    """.trimIndent()

    /**
     * Avanza a la siguiente página de movimientos si el portal usa paginación (incluyendo iframes):
     * 1) Dinamica nativo de Bancaribe: `pageNext()` cuando `currentPage < lastPage`.
     * 2) jQuery DataTables: `dt.page('next').draw('page')`.
     * 3) Botones/enlaces de paginación Materialize/DataTables/ASP.NET GridView (`.paginate_button.next`, `.pagination li.active + li a`, "Siguiente", "Ver más").
     */
    fun buildNextPageScript(): String = """
        (function(){
          try {
            $ALL_DOCS_JS
            function txt(el){ return ((el && (el.innerText || el.textContent)) || '').replace(/\s+/g,' ').trim(); }
            var docs = allDocs();

            for (var d = 0; d < docs.length; d++) {
              var doc = docs[d];
              var win = doc.defaultView || window;

              // 1. Dinamica nativo (Bancaribe): currentPage < lastPage && pageNext()
              if (typeof win.pageNext === 'function' && typeof win.currentPage === 'number' && typeof win.lastPage === 'number' && win.lastPage > 1 && win.currentPage < win.lastPage) {
                win.pageNext();
                return JSON.stringify({ status: 'next', method: 'dinamica_pageNext', page: win.currentPage });
              }

              // 2. jQuery DataTables
              if (typeof win.${'$'} === 'function' && win.${'$'}.fn && win.${'$'}.fn.DataTable) {
                var tables = doc.querySelectorAll('table');
                for (var t = 0; t < tables.length; t++) {
                  try {
                    if (win.${'$'}.fn.DataTable.isDataTable(tables[t])) {
                      var dt = win.${'$'}(tables[t]).DataTable();
                      var info = dt.page.info();
                      if (info && info.page + 1 < info.pages) {
                        dt.page('next').draw('page');
                        return JSON.stringify({ status: 'next', method: 'datatable_next', page: info.page + 2, pages: info.pages });
                      }
                    }
                  } catch (_e) {}
                }
              }

              // 3. Selectores estándar de siguiente página
              var nextSelectors = [
                '.paginate_button.next:not(.disabled):not(.paginate_button_disabled)',
                'li.paginate_button.next:not(.disabled) a',
                'li.next:not(.disabled) a',
                'a.next:not(.disabled)',
                '.pagination li.active + li:not(.disabled) a',
                'button[aria-label*="siguiente" i]:not([disabled])',
                'a[aria-label*="siguiente" i]:not(.disabled)',
                '[onclick*="pageNext" i]',
                'input[name*=" Next" i]:not([disabled])',
                'input[id*="Next" i]:not([disabled])'
              ];
              for (var s = 0; s < nextSelectors.length; s++) {
                var el = doc.querySelector(nextSelectors[s]);
                if (el && visible(el) && !el.classList.contains('disabled') && (!el.parentElement || !el.parentElement.classList.contains('disabled'))) {
                  el.click();
                  return JSON.stringify({ status: 'next', method: 'selector', target: nextSelectors[s] });
                }
              }

              // 4. Botones o enlaces con texto "Siguiente", "Ver más", "Cargar más", o número siguiente en GridView ASP.NET
              var cands = doc.querySelectorAll('.pagination a, .dataTables_paginate a, tr.pager a, a.btn, button.btn, a, button, input[type=submit], input[type=button]');
              for (var i = 0; i < cands.length; i++) {
                var c = cands[i];
                if (!visible(c) || c.disabled || c.classList.contains('disabled') || (c.parentElement && c.parentElement.classList.contains('disabled'))) continue;
                var label = (txt(c) || c.value || '').toLowerCase();
                var icon = c.querySelector ? c.querySelector('i.fa-chevron-right, i.fa-angle-right, i.material-icons') : null;
                var iconTxt = icon ? txt(icon).toLowerCase() : '';
                if (label === 'siguiente' || label === 'siguiente >' || label === 'ver más' || label === 'ver mas' ||
                    label === 'cargar más' || label === 'cargar mas' || label === 'mostrar más' || label === 'mostrar mas' ||
                    label === '›' || label === '»' || label === '>' ||
                    iconTxt === 'chevron_right' || iconTxt === 'navigate_next' ||
                    (icon && (icon.classList.contains('fa-chevron-right') || icon.classList.contains('fa-angle-right')) && c.closest('.pagination, .dataTables_paginate'))) {
                  c.click();
                  return JSON.stringify({ status: 'next', method: 'button_text', target: label || iconTxt || 'chevron_right' });
                }
              }
            }

            return JSON.stringify({ status: 'end' });
          } catch (e) {
            return JSON.stringify({ status: 'error', message: String(e) });
          }
        })();
    """.trimIndent()

    /** Extrae las filas de movimientos y el saldo. Devuelve un [PortalPayload]. */
    fun buildExtractScript(recipe: PortalRecipe): String {
        fun sel(value: String?) = value?.let { "'" + escapeJsString(it) + "'" } ?: "null"
        return """
            (function(){
              try {
                $ALL_DOCS_JS
                var docs = allDocs();
                // Si la tabla es un jQuery DataTable en memoria (como en Bancaribe), expandir a todas las filas (-1) antes de extraer.
                for (var dtDocIdx = 0; dtDocIdx < docs.length; dtDocIdx++) {
                  try {
                    var win = docs[dtDocIdx].defaultView || window;
                    if (typeof win.${'$'} === 'function' && win.${'$'}.fn && win.${'$'}.fn.DataTable) {
                      win.${'$'}('table').each(function(){
                        try {
                          if (win.${'$'}.fn.DataTable.isDataTable(this)) {
                            var dt = win.${'$'}(this).DataTable();
                            if (dt.page.info().pages > 1 && dt.page.len() !== -1) {
                              dt.page.len(-1).draw(false);
                            }
                          }
                        } catch (_e) {}
                      });
                    }
                  } catch (_e) {}
                }
                function txt(el){
                  if (!el) return '';
                  var raw = el.innerText;
                  if (!raw || !raw.trim()) {
                    raw = (el.textContent || '');
                  }
                  return raw.replace(/\s+/g,' ').trim();
                }
                function pick(row, selector){ if (!selector) return ''; var el = row.querySelector(selector); return el ? txt(el) : ''; }
                function isZeroAmt(s){
                  if (!s) return true;
                  var cleaned = s.replace(/[^0-9]/g, '');
                  return !cleaned || /^0+$/.test(cleaned);
                }
                var MONTHS = { ene:'01', feb:'02', mar:'03', abr:'04', may:'05', jun:'06', jul:'07', ago:'08', sep:'09', set:'09', oct:'10', nov:'11', dic:'12' };
                var AMT = /(?:[+-]\s*)?(?:\d{1,3}(?:[.,]\d{3})+|\d+)[.,]\d{2}(?!\d)/;
                var DATE_NUM = /\b(\d{1,2}[\/\-.]\d{1,2}[\/\-.]\d{2,4}|\d{4}-\d{2}-\d{2})\b/;
                var DATE_TXT = /\b(\d{1,2})[\s\/\-.]+(?:de\s+)?(ene|feb|mar|abr|may|jun|jul|ago|sep|set|oct|nov|dic)[a-z.]*[\s\/\-.]+(\d{2,4})\b/i;
                // Patrón directo de fila de movimiento Bancaribe: "15/10/2026 01:44 PM Ref: 001860797662 PAGO MOVIL REALIZADO Bs. -1.406,59"
                var INLINE_MOV = /^(\d{1,2}[\/\-]\d{1,2}[\/\-]\d{2,4})(?:\s+\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)?)?\s+(?:Ref\.?:?\s*([0-9]+)\s+)?(.+?)\s+(?:Bs\.?|VES|USD|\$)\s*([+-]?\s*(?:\d{1,3}(?:[.,]\d{3})+|\d+)[.,]\d{2})/i;
                // Patrón de tarjeta resumen de Posición Consolidada / Directorio de Productos (NO es un movimiento).
                var SUMMARY_CARD = /paga\s+antes\s+del|pago\s+m[ií]n|deuda\s+bs|disp\.\s*bs|saldo\s+disponible|saldo\s+total|posici[oó]n\s+consolidada|directorio\s+de\s+productos|\b\d{4}\*{2,6}\d{3,4}\b|\b0114[\d*]{8,16}\b|\b0134[\d*]{8,16}\b|\b0191[\d*]{8,16}\b/i;
                function extractDate(str) {
                  if (!str) return '';
                  var m1 = str.match(DATE_NUM);
                  if (m1) return m1[1].replace(/-/g, '/');
                  var m2 = str.match(DATE_TXT);
                  if (m2) {
                    var d = m2[1].length < 2 ? ('0' + m2[1]) : m2[1];
                    var mon = MONTHS[m2[2].slice(0,3).toLowerCase()] || '01';
                    var y = m2[3].length === 2 ? ('20' + m2[3]) : m2[3];
                    return d + '/' + mon + '/' + y;
                  }
                  return '';
                }
                if (${recipe.loginFormMarkerJs}) { return JSON.stringify({ status: 'login', url: location.href }); }
                for (var lgIdx = 0; lgIdx < docs.length; lgIdx++) {
                  var lgPass = docs[lgIdx].querySelector('input[type=password], input#txtClave');
                  var lgUser = docs[lgIdx].querySelector('input#txtUsuario, input#userlogin, input#CardNumber');
                  if ((lgPass && visible(lgPass)) || (lgUser && visible(lgUser))) {
                    return JSON.stringify({ status: 'login', url: location.href });
                  }
                }
                var out = { status: 'ok', url: location.href, balanceText: null, rows: [], accounts: [] };
                var seenAccIdx = {};
                function addAccountBalance(kind, currency, amountText, mask, label, rawText) {
                  var cleanAmt = (amountText || '').replace(/\s+/g, '');
                  var raw = rawText || '';
                  if (!cleanAmt && !raw) return;
                  var k = kind + '|' + currency + '|' + (mask || label || cleanAmt);
                  var entry = {
                    kind: kind,
                    currency: currency,
                    amountText: cleanAmt,
                    mask: mask || '',
                    label: label || '',
                    rawText: raw.length > 600 ? raw.slice(0, 600) : raw
                  };
                  // Mismo producto visto desde dos contenedores (la tarjeta y su
                  // sección): se queda el texto MÁS CORTO, que es el del bloque
                  // de la tarjeta y no arrastra al de las demás.
                  if (seenAccIdx[k] !== undefined) {
                    var prev = out.accounts[seenAccIdx[k]];
                    if (entry.rawText && (!prev.rawText || entry.rawText.length < prev.rawText.length)) {
                      out.accounts[seenAccIdx[k]] = entry;
                    }
                    return;
                  }
                  seenAccIdx[k] = out.accounts.length;
                  out.accounts.push(entry);
                  if (kind !== 'CREDIT_CARD' && currency === '${escapeJsString(recipe.currency)}' && !out.balanceText && cleanAmt) {
                    out.balanceText = cleanAmt;
                  }
                }
                // Escanear Directorio de Productos (BanescOnline) y Posición Consolidada (Bancaribe / BNC) para capturar saldos de todas las cuentas.
                for (var pDocIdx = 0; pDocIdx < docs.length; pDocIdx++) {
                  var pNodes = docs[pDocIdx].querySelectorAll('tr, .collapsible-div, .collection-item, .card-panel, .card, .list-group-item, li.list-group-item, div.product, div.account, div[class*="product" i], div[class*="account" i], div[class*="card" i], div[class*="item" i], div[class*="balance" i], div.row');
                  for (var pIdx = 0; pIdx < pNodes.length; pIdx++) {
                    var pNode = pNodes[pIdx];
                    if (pNode.tagName !== 'TR' && pNode.querySelector('table, tr, .collapsible-div, .collection-item, .list-group-item, div[class*="product" i]')) continue;
                    var pText = txt(pNode);
                    var pSib = (pNode.classList && (pNode.classList.contains('collapsible-div') || pNode.classList.contains('collapsible-header')))
                               ? pNode.nextElementSibling : null;
                    if (pSib && pSib.classList && !pSib.classList.contains('collapsible-div')) {
                      pText = (pText + ' ' + txt(pSib)).trim();
                    }
                    if (!pText || pText.length > 600) continue;
                    var isTdcRow = /\b(visa|mastercard|american\s+express|amex|tdc|tarjeta\s+de\s+cr[eé]dito)\b|(?:^|[\s(])[45]\d{3}[\d*•-]{4,16}\b|deuda\s+bs|pago\s+m[ií]n(?:imo)?|cupo/i.test(pText);
                    // Un contenedor con dos tarjetas juntaría la deuda de una con
                    // el disponible de otra: esas filas se descartan.
                    var cardMaskHits = pText.match(/(?:^|[\s(])(?:[45]\d{3}[\d*•-]{4,16}|\*{2,8}\d{3,4})\b/g);
                    if (isTdcRow && cardMaskHits && cardMaskHits.length > 1) continue;
                    var accMaskMatch = pText.match(/(?:^|[\s(])(01(?:34|14|91)[\d*•-]{4,20}|[*•]{2,8}\d{3,4})\b/);
                    var isDepositSummary = !isTdcRow && (accMaskMatch || (!extractDate(pText) && /\b(?:cuenta|cta\.?)\s*(?:corriente|de\s+ahorro|ahorro|verde|efectivo|moneda\s+extranjera|libre\s+conv|[uú]nica)?\b/i.test(pText)));
                    if (isDepositSummary) {
                      var isUsd = /cuenta\s+usd|cuenta\s+verde|moneda\s+extranjera|divisa|\busd\b|\buss\b|d[oó]lares|libre\s+conv|efectivo/i.test(pText);
                      var curr = isUsd ? 'USD' : '${escapeJsString(recipe.currency)}';
                      var balVal = '';
                      var mDisp = pText.match(/(?:disp(?:onible)?\.?\s*(?:\(?(?:bs\.?|usd|\$)\)?)?|saldo\s+disponible\s*(?:\(?(?:bs\.?|usd|\$)\)?)?)\s*:?\s*((?:\d{1,3}(?:[.,]\d{3})+|\d+)[.,]\d{2}(?!\d))/i);
                      if (mDisp) {
                        balVal = mDisp[1];
                      } else if (pNode.tagName === 'TR') {
                        var pTds = pNode.querySelectorAll('td');
                        var pTbl = pNode.closest ? pNode.closest('table') : null;
                        if (pTbl && pTds.length >= 2) {
                          var pHdrs = pTbl.querySelectorAll('thead tr th, thead tr td, tr:first-child th, tr:first-child td');
                          if (pHdrs.length === pTds.length) {
                            for (var ph = 0; ph < pHdrs.length; ph++) {
                              var phTxt = txt(pHdrs[ph]).toLowerCase();
                              if (/diferido|bloqueado|promedio|corte|reserva|retenido/i.test(phTxt)) continue;
                              if (/\b(saldo|disponible|total|actual)\b/i.test(phTxt)) {
                                var mCol = txt(pTds[ph]).match(AMT);
                                if (mCol) { balVal = mCol[0]; break; }
                              }
                            }
                          }
                        }
                        if (!balVal) {
                          for (var pt = 0; pt < pTds.length; pt++) {
                            var tdStr = txt(pTds[pt]);
                            if (/\b01(?:34|14|91)[\d*•-]{4,20}\b/.test(tdStr)) continue;
                            var mTd = tdStr.match(AMT);
                            if (mTd) { balVal = mTd[0]; break; }
                          }
                        }
                      }
                      if (!balVal) {
                        var mAmts = pText.match(new RegExp(AMT.source, 'g'));
                        if (mAmts && mAmts.length > 0) {
                          var nonZeroAmt = null;
                          for (var mi = 0; mi < mAmts.length; mi++) {
                            if (!isZeroAmt(mAmts[mi])) { nonZeroAmt = mAmts[mi]; break; }
                          }
                          balVal = nonZeroAmt || mAmts[0];
                        }
                      }
                      if (balVal) {
                        var lbl = isUsd ? (pText.indexOf('Efectivo') >= 0 ? 'Cuenta USD Efectivo' : (pText.indexOf('Libre Conv') >= 0 ? 'Cuenta USD Libre Conv' : 'Cuenta USD')) : 'Cuenta';
                        addAccountBalance('CHECKING', curr, balVal, accMaskMatch ? accMaskMatch[1] : '', lbl);
                      }
                    } else if (isTdcRow && (!extractDate(pText) || /paga\s+antes\s+del|pago\s+m[ií]n|deuda\s+bs|mastercard|visa/i.test(pText))) {
                      var CUR_OPT = '(?:\\s*\\(?\\s*(?:bs\\.?|usd|us\\$|\\$)\\s*\\)?)?';
                      var AMT_OPT = '((?:\\d{1,3}(?:[.,]\\d{3})+|\\d+)[.,]\\d{2}(?!\\d))';
                      var mDeuda = pText.match(new RegExp('(?:deuda|saldo\\s+actual|saldo\\s+deudor|utilizado|consumo)' + CUR_OPT + '\\s*:?\\s*' + AMT_OPT, 'i'));
                      var mDisp = pText.match(new RegExp('(?:disp(?:onible)?\\.?|saldo\\s+disponible)' + CUR_OPT + '\\s*:?\\s*' + AMT_OPT, 'i'));
                      var debtVal = mDeuda ? mDeuda[1] : '';
                      var dispVal = mDisp ? mDisp[1] : '';
                      var tdcKind = debtVal ? 'TDC_DEBT' : (dispVal ? 'TDC_AVAILABLE' : 'TDC');
                      if (pNode.tagName === 'TR') {
                        var tTds = pNode.querySelectorAll('td');
                        var tTbl = pNode.closest ? pNode.closest('table') : null;
                        if (tTbl && tTds.length >= 2) {
                          var tHdrs = tTbl.querySelectorAll('thead tr th, thead tr td, tr:first-child th, tr:first-child td');
                          if (tHdrs.length === tTds.length) {
                            for (var thIdx = 0; thIdx < tHdrs.length; thIdx++) {
                              var hTxt = txt(tHdrs[thIdx]).toLowerCase();
                              var mCol = txt(tTds[thIdx]).match(AMT);
                              if (!mCol) continue;
                              if (!debtVal && /\b(deuda|saldo\s+actual|saldo\s+al\s+corte|saldo\s+deudor|utilizado)\b/i.test(hTxt)) {
                                debtVal = mCol[0];
                                tdcKind = 'TDC_DEBT';
                              } else if (!dispVal && /\b(disp|disponible)\b/i.test(hTxt)) {
                                dispVal = mCol[0];
                                tdcKind = 'TDC_AVAILABLE';
                              }
                            }
                          }
                        }
                      }
                      if (!debtVal && !dispVal) {
                        var mTdcAmts = pText.match(new RegExp(AMT.source, 'g'));
                        if (mTdcAmts && mTdcAmts.length > 0) {
                          debtVal = mTdcAmts[mTdcAmts.length - 1];
                          tdcKind = 'TDC';
                        }
                      }
                      // La deuda manda como saldo de la cuenta; el disponible viaja
                      // aparte para poder mostrar la tarjeta completa. El texto
                      // crudo lo reinterpreta PortalTdcParser en Kotlin.
                      var finalVal = debtVal || dispVal;
                      var cardMaskMatch = pText.match(/(?:^|[\s(])([45]\d{3}[\d*•-]{4,16}|[*•]{2,8}\d{3,4})\b/);
                      addAccountBalance(
                        'CREDIT_CARD',
                        'VES',
                        finalVal ? '-' + finalVal.replace(/^-/, '') : '',
                        cardMaskMatch ? cardMaskMatch[1] : '',
                        tdcKind,
                        pText
                      );
                    }
                  }
                }
                var maxRows = ${recipe.maxRows};
                var seenKeys = {};
                var lastGroupDate = '';
                for (var dIdx = 0; dIdx < docs.length; dIdx++) {
                  var doc = docs[dIdx];
                  var nodes = doc.querySelectorAll('${escapeJsString(recipe.rowsSelector)}');
                  for (var i = 0; i < nodes.length && out.rows.length < maxRows; i++) {
                    var node = nodes[i];
                    if (node.tagName !== 'TR' && node.querySelector('table, tr, .collapsible-div, .collection-item')) continue;
                    // Ignorar filas de cabecera (<tr> que solo tiene <th>)
                    if (node.tagName === 'TR' && node.querySelectorAll('th').length > 0 && node.querySelectorAll('td').length === 0) continue;
                    var visibleText = txt(node);
                    var sib = (node.classList && (node.classList.contains('collapsible-div') || node.classList.contains('collapsible-header')))
                              ? node.nextElementSibling : null;
                    var whole = visibleText;
                    if (sib && sib.classList && !sib.classList.contains('collapsible-div') && !sib.classList.contains('collapsible-header')) {
                      whole = (visibleText + ' ' + txt(sib)).trim();
                    }
                    if (!whole || whole.length > 700) continue;
                    // Ignorar tarjetas de resumen de cuenta o TDC en Posición Consolidada / Directorio de Productos.
                    if (SUMMARY_CARD.test(whole)) continue;
                    var headerOnlyDate = extractDate(whole);
                    if (headerOnlyDate && !AMT.test(whole) && whole.length < 40) {
                      lastGroupDate = headerOnlyDate;
                      continue;
                    }
                    var row = {
                      date: pick(node, ${sel(recipe.cellDateSelector)}),
                      description: pick(node, ${sel(recipe.cellDescriptionSelector)}),
                      amount: pick(node, ${sel(recipe.cellAmountSelector)}),
                      debit: pick(node, ${sel(recipe.cellDebitSelector)}),
                      credit: pick(node, ${sel(recipe.cellCreditSelector)}),
                      reference: pick(node, ${sel(recipe.cellReferenceSelector)}),
                      balance: '',
                      raw: visibleText || whole
                    };
                    // 1) Intentar match directo de fila de movimiento estilo Bancaribe ("dd/MM/yyyy hh:mm AM Ref: ... DESC Bs. -1.234,56")
                    var inlineMatch = (visibleText || whole).match(INLINE_MOV);
                    if (inlineMatch) {
                      row.date = inlineMatch[1].replace(/-/g, '/');
                      if (inlineMatch[2]) row.reference = inlineMatch[2].trim();
                      row.description = inlineMatch[3].trim();
                      row.amount = inlineMatch[4].replace(/\s+/g, '');
                    }
                    // 2) Si es un <tr> dentro de un <table> con cabeceras (como en BanescOnline: Fecha | Referencia | Concepto | Cargo | Abono | Saldo),
                    // detectar columnas por cabecera para separar con precisión Cargo (debit), Abono (credit) y Saldo.
                    if (node.tagName === 'TR' && !inlineMatch) {
                      var tds = node.querySelectorAll('td');
                      var tbl = node.closest ? node.closest('table') : null;
                      if (tbl && tds.length >= 3) {
                        var hdrCells = tbl.querySelectorAll('thead tr th, thead tr td, tr:first-child th, tr:first-child td');
                        if (hdrCells.length === tds.length) {
                          for (var h = 0; h < hdrCells.length; h++) {
                            var hTxt = txt(hdrCells[h]).toLowerCase();
                            var cellVal = txt(tds[h]);
                            if (!cellVal) continue;
                            if (!row.date && /\b(fecha|d[ií]a)\b/.test(hTxt)) {
                              var dVal = extractDate(cellVal);
                              if (dVal) row.date = dVal;
                            } else if (!row.reference && /\b(ref|referencia|nro|n[uú]mero|documento|comprobante|serial)\b/.test(hTxt)) {
                              if (!/^(ref|referencia|nro)/i.test(cellVal)) row.reference = cellVal;
                            } else if (!row.description && /\b(descripci[oó]n|concepto|detalle|operaci[oó]n|movimiento)\b/.test(hTxt)) {
                              if (!/^(descripci|concepto|detalle)/i.test(cellVal)) row.description = cellVal;
                            } else if (/\b(cargo|d[eé]bito|debe|retiro|egreso)\b/.test(hTxt)) {
                              var mDeb = cellVal.match(AMT);
                              if (mDeb && !isZeroAmt(mDeb[0])) row.debit = mDeb[0].replace(/\s+/g, '');
                            } else if (/\b(abono|cr[eé]dito|haber|dep[oó]sito|ingreso)\b/.test(hTxt)) {
                              var mCred = cellVal.match(AMT);
                              if (mCred && !isZeroAmt(mCred[0])) row.credit = mCred[0].replace(/\s+/g, '');
                            } else if (/\b(saldo|disponible)\b/.test(hTxt)) {
                              var mBal = cellVal.match(AMT);
                              if (mBal) row.balance = mBal[0].replace(/\s+/g, '');
                            } else if (!row.amount && /\b(monto|importe|valor)\b/.test(hTxt)) {
                              var mAmtCol = cellVal.match(AMT);
                              if (mAmtCol) {
                                var aCol = mAmtCol[0].replace(/\s+/g, '');
                                row.amount = aCol;
                              }
                            }
                          }
                        }
                      }
                    }
                    if (!row.date || (!row.amount && !row.debit && !row.credit)) {
                      var cells = node.querySelectorAll('td,th,mat-cell,[role=cell],[role=gridcell]');
                      if (cells.length < 2) {
                        var leafSel = 'div,span,p,label,b,strong,td,a';
                        var rawCells = node.querySelectorAll(leafSel);
                        var combined = [];
                        for (var rc = 0; rc < rawCells.length; rc++) combined.push(rawCells[rc]);
                        if (sib) {
                          var sibCells = sib.querySelectorAll(leafSel);
                          for (var sc = 0; sc < sibCells.length; sc++) combined.push(sibCells[sc]);
                        }
                        cells = combined;
                      }
                      var texts = [];
                      for (var c = 0; c < cells.length; c++) {
                        var elCell = cells[c];
                        if (elCell.tagName !== 'TD' && elCell.tagName !== 'TH' && elCell.children && elCell.children.length > 3) continue;
                        var ct = txt(elCell);
                        if (ct && texts.indexOf(ct) < 0) texts.push(ct);
                      }
                      if (texts.length === 0) texts.push(whole);
                      var joined = texts.join(' | ');
                      var foundDate = extractDate(joined) || extractDate(whole) || lastGroupDate;
                      if (!foundDate || !AMT.test(joined)) continue;
                      if (!extractDate(joined) && !extractDate(whole) && /cuenta\s+(corriente|de\s+ahorro|verde|[uú]nica)|saldo/i.test(whole)) continue;
                      if (!row.date) { row.date = foundDate; }
                      if (!row.amount && !row.debit && !row.credit) {
                        var amountCandidates = [];
                        for (var a = 0; a < texts.length; a++) {
                          if (extractDate(texts[a]) && texts[a].length < 25) continue;
                          var mAmt = texts[a].match(AMT);
                          if (mAmt) {
                            var amtStr = mAmt[0].replace(/\s+/g, '');
                            if (/\b(debito|débito|cargo|db)\b/i.test(texts[a]) && amtStr[0] !== '-') amtStr = '-' + amtStr;
                            var hasCurrency = /(?:bs\.?|ves|usd|\$)/i.test(texts[a]);
                            amountCandidates.push({ val: amtStr, currency: hasCurrency, zero: isZeroAmt(amtStr) });
                          }
                        }
                        if (amountCandidates.length > 0) {
                          var nonZero = [];
                          for (var nz = 0; nz < amountCandidates.length; nz++) {
                            if (!amountCandidates[nz].zero) nonZero.push(amountCandidates[nz]);
                          }
                          var pool = nonZero.length > 0 ? nonZero : amountCandidates;
                          var chosen = pool[0];
                          for (var ac = 0; ac < pool.length; ac++) {
                            if (pool[ac].currency) { chosen = pool[ac]; break; }
                          }
                          row.amount = chosen.val;
                          var lastCand = amountCandidates[amountCandidates.length - 1];
                          if (amountCandidates.length > 1 && lastCand !== chosen && !lastCand.zero) {
                            row.balance = lastCand.val;
                          }
                        } else {
                          var mWhole = joined.match(AMT);
                          if (mWhole) row.amount = mWhole[0].replace(/\s+/g, '');
                        }
                      }
                      if (!row.reference) {
                        var refMatch = joined.match(/(?:ref(?:erencia)?\.?|nro\.?|documento|secuencia|comprobante)[:\s#]*([0-9]{4,20})/i);
                        if (refMatch) {
                          row.reference = refMatch[1];
                        } else {
                          for (var rf = 0; rf < texts.length; rf++) {
                            if (/^\d{5,20}$/.test(texts[rf])) {
                              row.reference = texts[rf];
                              break;
                            }
                          }
                        }
                      }
                      if (!row.description) {
                        var best = '', len = 0;
                        for (var d = 0; d < texts.length; d++) {
                          var v = texts[d];
                          if (AMT.test(v) || extractDate(v) || /^\d{4,20}$/.test(v) || /^ref\.?:?\s*\d+$/i.test(v) || /^(fecha|descripci[oó]n|concepto|monto|cargo|abono|saldo|referencia|detalle)$/i.test(v)) continue;
                          if (v.length > len && v.length < 180) { len = v.length; best = v; }
                        }
                        row.description = best || whole.slice(0, 100);
                      }
                    }
                    if (!row.date) continue;
                    if (!row.amount && !row.debit && !row.credit) continue;
                    var dedupKey = row.date + '|' + row.amount + '|' + row.debit + '|' + row.credit + '|' + row.description + '|' + row.reference;
                    if (seenKeys[dedupKey]) continue;
                    seenKeys[dedupKey] = true;
                    out.rows.push(row);
                  }
                }
                var balSel = ${sel(recipe.balanceSelector)};
                if (balSel && !out.balanceText) {
                  for (var bIdx = 0; bIdx < docs.length && !out.balanceText; bIdx++) {
                    var balEls = docs[bIdx].querySelectorAll(balSel);
                    for (var k = 0; k < balEls.length; k++) {
                      var bt = txt(balEls[k]);
                      var container = balEls[k].closest ? balEls[k].closest('.card-panel, .card, .collapsible-div, .collection-item, tr') : null;
                      var ctxTxt = bt + ' ' + (container ? txt(container) : '');
                      if (/visa|mastercard|deuda|pago\s+m[ií]n|paga\s+antes\s+del|\b[45]\d{3}\*{2,6}\d{3,4}\b/i.test(ctxTxt)) continue;
                      if (/diferido|bloqueado|promedio|corte/i.test(bt)) continue;
                      var m = bt.match(AMT);
                      if (!m && balEls[k].nextElementSibling) {
                        m = txt(balEls[k].nextElementSibling).match(AMT);
                      }
                      if (!m && balEls[k].parentElement && balEls[k].parentElement.nextElementSibling) {
                        m = txt(balEls[k].parentElement.nextElementSibling).match(AMT);
                      }
                      if (m) { out.balanceText = m[0].replace(/\s+/g, ''); break; }
                    }
                  }
                }
                // En BanescOnline Vista de Movimientos, buscar etiquetas "Saldo Disponible:" / "Saldo Actual:" con el monto en la celda vecina.
                if (!out.balanceText) {
                  for (var lblDocIdx = 0; lblDocIdx < docs.length && !out.balanceText; lblDocIdx++) {
                    var lbls = docs[lblDocIdx].querySelectorAll('td, th, span, div, label, b, strong');
                    for (var l = 0; l < lbls.length; l++) {
                      var lt = txt(lbls[l]);
                      if (/^(?:saldo\s+disponible|saldo\s+actual|saldo\s+en\s+l[ií]nea|disponible)\s*:?$/i.test(lt)) {
                        var nextEl = lbls[l].nextElementSibling || (lbls[l].parentElement && lbls[l].parentElement.nextElementSibling);
                        if (nextEl) {
                          var mNext = txt(nextEl).match(AMT);
                          if (mNext) { out.balanceText = mNext[0].replace(/\s+/g, ''); break; }
                        }
                      }
                    }
                  }
                }
                if (!out.balanceText && out.rows.length > 0 && out.rows[0].balance) {
                  out.balanceText = out.rows[0].balance;
                }
                if (!out.balanceText) {
                  var fullBody = '';
                  for (var fb = 0; fb < docs.length; fb++) {
                    fullBody += ' ' + txt(docs[fb].body);
                  }
                  var body = fullBody.slice(0, 40000);
                  var bm = body.match(/(?:\b01(?:14|34)[\d*•-]{4,20}[^0-9]{0,90}(?:disp(?:onible)?\.?\s*(?:bs\.?)?|saldo[^0-9-]{0,25}|bs\.?\s*)|cuenta\s+(?:corriente|de\s+ahorro|verde|[uú]nica|bancaribe|banesco)[^0-9]{0,90}(?:disp(?:onible)?\.?\s*(?:bs\.?)?|saldo[^0-9-]{0,25}|bs\.?\s*)|saldo\s+disponible[^0-9-]{0,40}|saldo\s+actual[^0-9-]{0,40})((?:\d{1,3}(?:[.,]\d{3})+|\d+)[.,]\d{2})/i);
                  if (bm) out.balanceText = bm[1].replace(/\s+/g, '');
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
