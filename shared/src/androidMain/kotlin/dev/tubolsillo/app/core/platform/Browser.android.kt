package dev.tubolsillo.app.core.platform

import android.content.Intent
import android.net.Uri
import org.koin.core.context.GlobalContext

actual fun openUrl(url: String) {
    val context = GlobalContext.get().get<android.content.Context>()
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
