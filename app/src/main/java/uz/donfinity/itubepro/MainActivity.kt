package uz.donfinity.itubepro

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.core.player.PlayerConnection
import uz.donfinity.itubepro.navigation.AppNavHost
import uz.donfinity.itubepro.ui.player.PipSourceRect
import uz.donfinity.itubepro.ui.player.isPipEligible
import uz.donfinity.itubepro.ui.player.toPipParams
import uz.donfinity.itubepro.ui.theme.ItubeProTheme

/**
 * The only Activity. Screens are Compose destinations inside [AppNavHost].
 *
 * Picture-in-Picture: closing the PiP window (the X) only stops this activity, not playback.
 * The video keeps going as background audio, controllable from the notification.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playerConnection: PlayerConnection

    private val supportsPip by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ItubeProTheme {
                AppNavHost()
            }
        }
        if (supportsPip) keepPipParamsUpdated()
    }

    // Connect to the player only while the UI is visible. Playback itself lives in
    // PlaybackService and doesn't depend on this connection. In PiP the activity is paused
    // but still started, so the connection (and the PiP video) stays alive.
    override fun onStart() {
        super.onStart()
        playerConnection.connect()
    }

    override fun onStop() {
        playerConnection.disconnect()
        super.onStop()
    }

    /**
     * Android 12+ enters PiP by itself when we enable auto-enter. We keep the params in sync
     * with the player: only while a video plays, with the video's aspect ratio.
     */
    private fun keepPipParamsUpdated() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(playerConnection.state, PipSourceRect.bounds, ::Pair).collect { (state, rect) ->
                    setPictureInPictureParams(state.toPipParams(autoEnter = state.isPipEligible(), sourceRect = rect))
                }
            }
        }
    }

    /** Android 8–11: the user pressed Home / switched apps. Enter PiP ourselves. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val state = playerConnection.state.value
        if (supportsPip && Build.VERSION.SDK_INT < Build.VERSION_CODES.S && state.isPipEligible()) {
            enterPictureInPictureMode(state.toPipParams(autoEnter = false, sourceRect = PipSourceRect.bounds.value))
        }
    }
}
