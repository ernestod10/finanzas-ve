package dev.tubolsillo.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.tubolsillo.app.R
import dev.tubolsillo.app.core.di.sharedKoin
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.repo.RatesRefresher
import dev.tubolsillo.app.domain.repo.SettingsRepository
import dev.tubolsillo.app.ui.theme.AppAccent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

/**
 * Widget calculadora: se escribe un monto en dólares con el teclado del propio
 * widget y muestra el equivalente en bolívares a BCV y a Binance P2P.
 *
 * Los widgets no admiten campos de texto, así que el "campo" es un estado en
 * SharedPreferences: cada tecla envía un broadcast, se recalcula y se repinta
 * con `partialUpdateAppWidget`.
 */
class RatesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val views = render(context)
                appWidgetManager.updateAppWidget(appWidgetIds, views)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val key = intent.getStringExtra(EXTRA_KEY)
        when (intent.action) {
            ACTION_KEY -> {
                val prefs = prefs(context)
                val current = prefs.getString(KEY_INPUT, "") ?: ""
                val next = when (key) {
                    KEY_DELETE -> current.dropLast(1)
                    KEY_COMMA -> if (current.contains(',')) current else if (current.isEmpty()) "0," else current + ","
                    else -> if (current.length < 12) current + key else current
                }
                prefs.edit().putString(KEY_INPUT, next).apply()
                repaint(context)
            }

            ACTION_REFRESH -> {
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        runCatching { sharedKoin<RatesRefresher>().refresh() }
                        val manager = AppWidgetManager.getInstance(context)
                        val ids = manager.getAppWidgetIds(ComponentName(context, RatesWidgetProvider::class.java))
                        manager.updateAppWidget(ids, render(context))
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun repaint(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, RatesWidgetProvider::class.java))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, render(context))
    }

    private fun render(context: Context): RemoteViews {
        val settings = runCatching { sharedKoin<SettingsRepository>() }.getOrNull()
        val snapshot = settings?.ratesSnapshot?.value
        val bcv = snapshot?.bcvUsdBs ?: 0.0
        val p2p = snapshot?.p2pUsdtVes?.takeIf { it > 0 } ?: bcv

        val raw = prefs(context).getString(KEY_INPUT, "") ?: ""
        val amount = raw.replace(',', '.').toDoubleOrNull() ?: 0.0

        val views = RemoteViews(context.packageName, R.layout.widget_rates)

        // El widget sigue el tema de la app: modo oscuro y color de acento.
        val dark = settings?.let { repo ->
            when (repo.darkMode.value) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemDark(context)
            }
        } ?: isSystemDark(context)
        val accent = settings?.let { AppAccent.fromKey(it.accentColor.value) }
            ?.let { if (dark) it.primaryDark else it.primaryLight }
            ?.toArgb() ?: (if (dark) 0xFF5FD6A6.toInt() else 0xFF0E7A54.toInt())
        val ink = if (dark) 0xFFE9ECEA.toInt() else 0xFF101312.toInt()
        val muted = if (dark) 0xFF9AA09B.toInt() else 0xFF7A7F7B.toInt()

        views.setInt(
            R.id.widget_root, "setBackgroundResource",
            if (dark) R.drawable.widget_background_dark else R.drawable.widget_background,
        )
        listOf(
            R.id.key_0, R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4,
            R.id.key_5, R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9,
            R.id.key_comma, R.id.key_del,
        ).forEach { id ->
            views.setInt(
                id, "setBackgroundResource",
                if (dark) R.drawable.widget_key_dark else R.drawable.widget_key,
            )
            views.setTextColor(id, ink)
        }
        views.setTextColor(R.id.widget_input, ink)
        views.setTextColor(R.id.widget_title, muted)
        views.setTextColor(R.id.widget_bcv, ink)
        views.setTextColor(R.id.widget_p2p, accent)
        views.setTextColor(R.id.widget_refresh, accent)
        views.setTextViewText(R.id.widget_input, "$ " + if (raw.isBlank()) "0" else raw)
        views.setTextViewText(
            R.id.widget_bcv,
            if (bcv > 0) "Bs. " + bs(amount * bcv) else "—",
        )
        views.setTextViewText(
            R.id.widget_p2p,
            if (p2p > 0) "Bs. " + bs(amount * p2p) else "—",
        )

        // Teclado: cada tecla manda su acción; el estado vive en preferencias.
        listOf(
            R.id.key_1 to "1", R.id.key_2 to "2", R.id.key_3 to "3",
            R.id.key_4 to "4", R.id.key_5 to "5", R.id.key_6 to "6",
            R.id.key_7 to "7", R.id.key_8 to "8", R.id.key_9 to "9",
            R.id.key_0 to "0", R.id.key_comma to KEY_COMMA, R.id.key_del to KEY_DELETE,
        ).forEachIndexed { index, (viewId, key) ->
            views.setOnClickPendingIntent(
                viewId,
                PendingIntent.getBroadcast(
                    context,
                    ACTION_KEY.hashCode() + index,
                    Intent(context, RatesWidgetProvider::class.java)
                        .setAction(ACTION_KEY)
                        .putExtra(EXTRA_KEY, key),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }

        // Refrescar tasas sin abrir la app.
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            PendingIntent.getBroadcast(
                context,
                7,
                Intent(context, RatesWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )

        // Tocar el título abre la app.
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { open ->
                views.setOnClickPendingIntent(
                    R.id.widget_title,
                    PendingIntent.getActivity(
                        context,
                        0,
                        open,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
        return views
    }

    /** Bolívares con dos decimales y separador de miles local. */
    private fun bs(value: Double): String =
        MoneyFormatter.formatMinor((value * 100).roundToLong(), "VES", withSymbol = false)

    private fun isSystemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun prefs(context: Context) = context.getSharedPreferences("rates_widget", Context.MODE_PRIVATE)

    companion object {
        private const val ACTION_KEY = "dev.tubolsillo.app.widget.KEY"
        private const val ACTION_REFRESH = "dev.tubolsillo.app.widget.REFRESH"
        private const val EXTRA_KEY = "key"
        private const val KEY_DELETE = "DEL"
        private const val KEY_COMMA = ","
        private const val KEY_INPUT = "input"

        /** Refresca los widgets (lo llama la app al cambiar las tasas). */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RatesWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, RatesWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }
}
