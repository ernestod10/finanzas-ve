package dev.tubolsillo.app.portal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Lo que la Activity del portal devuelve al gateway. */
sealed interface PortalActivityResult {
    data class Payload(val json: String, val url: String) : PortalActivityResult
    data class Linked(val url: String, val json: String? = null) : PortalActivityResult
    data object NeedsRelink : PortalActivityResult
    data object Cancelled : PortalActivityResult
    data class Failed(val reason: String) : PortalActivityResult
}

/**
 * Buzón entre la Activity del WebView y la corrutina que la lanzó. Sin esto, el
 * cierre con "atrás" dejaría al ViewModel colgado hasta el timeout mostrando un
 * spinner sobre una pantalla que el usuario ya abandonó.
 */
object PortalBroker {

    private val mutex = Mutex()
    private val pending = mutableMapOf<String, CompletableDeferred<PortalActivityResult>>()
    private val cancellers = mutableMapOf<String, () -> Unit>()
    private var counter = 0L

    suspend fun newRequest(): String = mutex.withLock {
        counter += 1
        val id = "portal-" + counter
        pending[id] = CompletableDeferred()
        id
    }

    suspend fun await(requestId: String): PortalActivityResult {
        val deferred = mutex.withLock { pending[requestId] } ?: return PortalActivityResult.Cancelled
        return deferred.await()
    }

    fun complete(requestId: String, result: PortalActivityResult) {
        val deferred = synchronized(this) { pending[requestId] }
        deferred?.complete(result)
    }

    suspend fun registerCanceller(requestId: String, onCancel: () -> Unit) = mutex.withLock {
        cancellers[requestId] = onCancel
    }

    suspend fun finish(requestId: String) = mutex.withLock {
        pending.remove(requestId)?.complete(PortalActivityResult.Cancelled)
        cancellers.remove(requestId)?.invoke()
        Unit
    }
}
