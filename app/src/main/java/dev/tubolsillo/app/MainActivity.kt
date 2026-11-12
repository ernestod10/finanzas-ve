package dev.tubolsillo.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.tubolsillo.app.domain.repo.SettingsRepository
import dev.tubolsillo.app.ui.App
import dev.tubolsillo.app.ui.lock.LockScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Host de la app. Además pinta la pantalla de bloqueo: si el usuario activó
 * "Bloqueo con huella o PIN" en Menú, al volver de fondo se tapa todo hasta
 * que el sistema confirme su identidad.
 */
class MainActivity : FragmentActivity() {

    private var locked by mutableStateOf(false)
    private var lockEnabled = false
    private val settings: SettingsRepository by lazy { GlobalContext.get().get() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            // Estado inicial: si el bloqueo está activo, arrancar tapado.
            lockEnabled = settings.appLockEnabled.first()
            locked = lockEnabled
            settings.appLockEnabled.collect { enabled ->
                lockEnabled = enabled
                if (!enabled) locked = false
            }
        }
        setContent {
            Box {
                App()
                if (locked) {
                    LockScreen(onUnlock = { promptUnlock() })
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Al salir de la app (no al abrir un diálogo) se vuelve a bloquear.
        if (lockEnabled) locked = true
    }

    private fun promptUnlock() {
        val manager = BiometricManager.from(this)
        val allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (manager.canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            // Sin huella ni PIN configurados: no se puede pedir nada, se deja pasar.
            locked = false
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked = false
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Desbloquear TuBolsillo")
                .setSubtitle("Huella o PIN del teléfono")
                .setAllowedAuthenticators(allowed)
                .build()
        )
    }
}
