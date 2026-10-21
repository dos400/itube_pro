package uz.donfinity.itubepro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import uz.donfinity.itubepro.core.player.PlayerConnection
import uz.donfinity.itubepro.navigation.AppNavHost
import uz.donfinity.itubepro.ui.theme.ItubeProTheme

/** The only Activity. Screens are Compose destinations inside [AppNavHost]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playerConnection: PlayerConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ItubeProTheme {
                AppNavHost()
            }
        }
    }

    // Connect to the player only while the UI is visible. Playback itself lives in
    // PlaybackService and doesn't depend on this connection.
    override fun onStart() {
        super.onStart()
        playerConnection.connect()
    }

    override fun onStop() {
        playerConnection.disconnect()
        super.onStop()
    }
}
