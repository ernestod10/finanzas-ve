package dev.ernesto.finanza

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.ernesto.finanza.ui.navigation.FinanzaNavHost
import dev.ernesto.finanza.ui.theme.FinanzaTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinanzaTheme {
                FinanzaNavHost()
            }
        }
    }
}
