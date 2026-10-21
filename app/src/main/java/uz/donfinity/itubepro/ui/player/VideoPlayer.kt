package uz.donfinity.itubepro.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlin.math.roundToInt

/**
 * Media3's PlayerView (a classic View) inside Compose. It works with a MediaController just
 * like with a local ExoPlayer: the controller forwards the video surface to the service's
 * player.
 *
 * @param useController false for the mini-player and PiP, where only the picture is shown.
 * @param onFullscreenClick shows PlayerView's own fullscreen button when non-null.
 * @param gestureListener enables double-tap seek and swipe volume/brightness when non-null.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    player: Player,
    modifier: Modifier = Modifier,
    useController: Boolean = true,
    isFullscreen: Boolean = false,
    onFullscreenClick: (() -> Unit)? = null,
    onControllerVisibilityChange: (Boolean) -> Unit = {},
    gestureListener: GesturePlayerLayout.Listener? = null,
    /** SponsorBlock segment starts, marked on the seek bar (PlayerView's "extra ad group" markers). */
    segmentMarkersMs: List<Long> = emptyList(),
) {
    // The big player (with controls) is what PiP should grow out of; the mini-player isn't.
    if (useController) {
        DisposableEffect(Unit) { onDispose { PipSourceRect.update(null) } }
    }
    val currentOnFullscreenClick by rememberUpdatedState(onFullscreenClick)
    val currentOnVisibility by rememberUpdatedState(onControllerVisibilityChange)
    AndroidView(
        factory = { context ->
            GesturePlayerLayout(context).apply {
                playerView.setShowNextButton(false)
                playerView.setShowPreviousButton(false)
                playerView.setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        currentOnVisibility(visibility == View.VISIBLE)
                    },
                )
            }
        },
        update = { layout ->
            layout.playerView.player = player
            layout.playerView.useController = useController
            layout.playerView.setFullscreenButtonState(isFullscreen)
            layout.playerView.setFullscreenButtonClickListener(
                if (onFullscreenClick != null) {
                    PlayerView.FullscreenButtonClickListener { currentOnFullscreenClick?.invoke() }
                } else {
                    null
                },
            )
            layout.gesturesEnabled = gestureListener != null
            layout.listener = gestureListener
            layout.playerView.setExtraAdGroupMarkers(
                segmentMarkersMs.toLongArray(),
                BooleanArray(segmentMarkersMs.size),
            )
        },
        // Detach so the old view stops holding a surface for the player.
        onRelease = { it.playerView.player = null },
        modifier = modifier
            .background(Color.Black)
            .then(
                if (useController) {
                    Modifier.onGloballyPositioned { coordinates ->
                        val b = coordinates.boundsInWindow()
                        PipSourceRect.update(Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt()))
                    }
                } else {
                    Modifier
                },
            ),
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
