package dev.tubolsillo.app.portal

import android.content.Context
import android.content.Intent
import dev.tubolsillo.app.core.platform.CurrentActivityHolder
import dev.tubolsillo.app.core.platform.PortalFetchResult
import dev.tubolsillo.app.core.platform.PortalGateway
import dev.tubolsillo.app.core.platform.PortalLinkResult
import dev.tubolsillo.app.data.source.portal.PortalJson

/** Lanza la Activity del portal y espera su resultado. */
class AndroidPortalGateway(
    private val context: Context,
    private val activityHolder: CurrentActivityHolder,
) : PortalGateway {

    override suspend fun link(institutionKey: String): PortalLinkResult {
        val id = PortalBroker.newRequest()
        launch(institutionKey, PortalWebActivity.MODE_LINK, id, null)
        return when (val result = PortalBroker.await(id)) {
            is PortalActivityResult.Linked -> PortalLinkResult.Linked(result.url)
            is PortalActivityResult.Failed -> PortalLinkResult.Failed(result.reason)
            else -> PortalLinkResult.Cancelled
        }.also { PortalBroker.finish(id) }
    }

    override suspend fun fetch(institutionKey: String, url: String): PortalFetchResult {
        val id = PortalBroker.newRequest()
        launch(institutionKey, PortalWebActivity.MODE_FETCH, id, url)
        return when (val result = PortalBroker.await(id)) {
            is PortalActivityResult.Payload -> {
                val payload = PortalJson.decode(result.json)
                if (payload == null) PortalFetchResult.Failed("El portal devolvió datos ilegibles.")
                else PortalFetchResult.Success(payload, result.url)
            }
            is PortalActivityResult.NeedsRelink -> PortalFetchResult.NeedsRelink
            is PortalActivityResult.Failed -> PortalFetchResult.Failed(result.reason)
            else -> PortalFetchResult.Cancelled
        }.also { PortalBroker.finish(id) }
    }

    private fun launch(institutionKey: String, mode: String, requestId: String, url: String?) {
        val host = activityHolder.activity
        val intent = PortalWebActivity.intent(host ?: context, institutionKey, mode, requestId, url)
        if (host != null) {
            host.startActivity(intent)
        } else {
            // Sin Activity en primer plano no queda otra que una task nueva.
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
