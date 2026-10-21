package uz.donfinity.itubepro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import uz.donfinity.itubepro.navigation.AppNavHost
import uz.donfinity.itubepro.ui.theme.ItubeProTheme

/** The only Activity. Screens are Compose destinations inside [AppNavHost]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ItubeProTheme {
                AppNavHost()
            }
        }
    }
}
