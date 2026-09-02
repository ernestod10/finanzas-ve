package dev.tubolsillo.app.data.source.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.SourceType
import dev.tubolsillo.app.domain.usecase.CapturePipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Captura SMS bancarios (SMS_RECEIVED está exento de las restricciones de
 * broadcasts implícitos; costo de batería ~0). Extrae cuerpo y remitente,
 * y los manda al CapturePipeline.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var pipeline: CapturePipeline

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            pendingResult.finish()
            return
        }
        val body = messages.joinToString(" ") { it.displayMessageBody ?: "" }
        val sender = messages.firstOrNull()?.displayOriginatingAddress
        if (body.isBlank()) {
            pendingResult.finish()
            return
        }

        scope.launch {
            try {
                pipeline.ingest(
                    rawText = body,
                    source = SourceType.SMS,
                    sender = sender,
                    hintedInstitution = null,
                )
            } catch (_: Exception) {
                // El pipeline persiste el evento FAILED; aquí solo no reventar el receiver.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
