package dev.tubolsillo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.tubolsillo.app.ui.navigation.TubolsilloNavHost
import dev.tubolsillo.app.ui.theme.TubolsilloTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TubolsilloTheme {
                TubolsilloNavHost()
            }
        }
    }
}
