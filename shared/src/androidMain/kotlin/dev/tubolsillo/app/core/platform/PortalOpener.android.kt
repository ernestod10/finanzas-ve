package dev.tubolsillo.app.core.platform

import android.content.Intent
import org.koin.core.context.GlobalContext

actual fun openPortalConnect(institutionKey: String) {
    val context = GlobalContext.get().get<android.content.Context>()
    runCatching {
        val intent = Intent().setClassName(
            context.packageName,
            "dev.tubolsillo.app.portal.PortalConnectActivity",
        ).putExtra("institution", institutionKey)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
