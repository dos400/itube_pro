package uz.donfinity.itubepro.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

/**
 * Media3's PlayerView (a classic View) inside Compose. It works with a MediaController just
 * like with a local ExoPlayer: the controller forwards the video surface to the service's
 * player.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(player: Player, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        update = { it.player = player },
        // Detach so the old view stops holding a surface for the player.
        onRelease = { it.player = null },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
    )
}

/**
 * Returns a click handler that, on Android 13+, first asks for the notification permission
 * (needed to show the media notification), then runs [onPlay] whatever the answer.
 * Playback works without the permission, just without a notification.
 */
@Composable
fun rememberPlayWithNotificationPermission(onPlay: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnPlay by rememberUpdatedState(onPlay)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        currentOnPlay()
    }
    return {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else currentOnPlay()
    }
}
