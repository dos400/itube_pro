package uz.donfinity.itubepro.ui.player

import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.core.player.VideoQuality

/** What the center overlay shows while a gesture is active. */
private sealed interface GestureFeedback {
    data class Volume(val fraction: Float) : GestureFeedback
    data class Brightness(val fraction: Float) : GestureFeedback
    data class Seek(val forward: Boolean, val tick: Long) : GestureFeedback
}

/**
 * Full-window video in its own Dialog window, so it covers everything (top bar, mini-player)
 * without changing the screens underneath. Landscape is requested while it's open; the
 * activity handles orientation changes itself (see the manifest), so nothing is recreated.
 */
@Composable
fun FullscreenPlayer(
    player: Player,
    qualities: List<VideoQuality>,
    selectedQuality: VideoQuality?,
    playingHeight: Int,
    onSelectQuality: (VideoQuality?) -> Unit,
    onExit: () -> Unit,
    segmentMarkersMs: List<Long> = emptyList(),
) {
    Dialog(
        onDismissRequest = onExit, // back button / gesture
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as DialogWindowProvider).window
        val view = LocalView.current
        val activity = LocalActivity.current
        val audioManager = remember(view) { view.context.getSystemService(AudioManager::class.java) }

        DisposableEffect(window) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Draw behind the camera cutout too.
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowCompat.getInsetsController(window, view).apply {
                // Bars stay hidden; a swipe from the edge shows them briefly.
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            val previousOrientation = activity?.requestedOrientation
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose {
                activity?.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        var feedback by remember { mutableStateOf<GestureFeedback?>(null) }
        var dragging by remember { mutableStateOf(false) }
        // PlayerView only reports changes, so start hidden and let its listener update this.
        var controlsVisible by remember { mutableStateOf(false) }
        var showQualities by remember { mutableStateOf(false) }

        // Hide the overlay shortly after the last gesture event.
        LaunchedEffect(feedback, dragging) {
            if (feedback != null && !dragging) {
                delay(FEEDBACK_HIDE_DELAY_MS)
                feedback = null
            }
        }

        val gestureListener = remember(player, window, audioManager) {
            object : GesturePlayerLayout.Listener {
                // Start values are read when a drag begins, so the gesture continues from the
                // current level instead of jumping.
                private var volume = -1f
                private var brightness = -1f

                override fun onDoubleTapSeek(forward: Boolean) {
                    if (forward) player.seekForward() else player.seekBack()
                    feedback = GestureFeedback.Seek(forward, System.nanoTime())
                }

                override fun onVerticalDrag(rightSide: Boolean, delta: Float) {
                    dragging = true
                    if (rightSide) {
                        // System media volume through AudioManager, the same as
                        // ref/NewPipe/.../player/helper/AudioReactor. Player.setDeviceVolume
                        // would keep this inside the Player API, but on device Media3 1.11 never
                        // passed COMMAND_SET_DEVICE_VOLUME* on to our MediaController (see
                        // CLAUDE.md, Phase 4). Changing the media volume needs no permission.
                        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        if (volume < 0) volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
                        volume = (volume + delta * DRAG_SENSITIVITY).coerceIn(0f, 1f)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * max).roundToInt(), 0)
                        feedback = GestureFeedback.Volume(volume)
                    } else {
                        // Window brightness overrides the system brightness only for this
                        // window, and goes away when fullscreen closes. No permission needed.
                        if (brightness < 0) {
                            brightness = window.attributes.screenBrightness.takeIf { it >= 0f } ?: DEFAULT_BRIGHTNESS
                        }
                        brightness = (brightness + delta * DRAG_SENSITIVITY).coerceIn(MIN_BRIGHTNESS, 1f)
                        window.attributes = window.attributes.apply { screenBrightness = brightness }
                        feedback = GestureFeedback.Brightness(brightness)
                    }
                }

                override fun onVerticalDragEnd() {
                    dragging = false
                    volume = -1f
                    brightness = -1f
                }
            }
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoPlayer(
                player = player,
                isFullscreen = true,
                onFullscreenClick = onExit,
                onControllerVisibilityChange = { controlsVisible = it },
                gestureListener = gestureListener,
                segmentMarkersMs = segmentMarkersMs,
                modifier = Modifier.fillMaxSize(),
            )
            feedback?.let { GestureFeedbackOverlay(it, Modifier.align(Alignment.Center)) }
            AnimatedVisibility(
                visible = controlsVisible && qualities.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            ) {
                TextButton(onClick = { showQualities = true }) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                    Text(
                        qualityButtonLabel(selectedQuality, playingHeight),
                        color = Color.White,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }

        if (showQualities) {
            QualitySheet(
                qualities = qualities,
                selected = selectedQuality,
                playingHeight = playingHeight,
                onSelect = {
                    onSelectQuality(it)
                    showQualities = false
                },
                onDismiss = { showQualities = false },
            )
        }
    }
}

@Composable
private fun GestureFeedbackOverlay(feedback: GestureFeedback, modifier: Modifier = Modifier) {
    val (icon: ImageVector, fraction: Float?, label: String) = when (feedback) {
        is GestureFeedback.Volume -> Triple(Icons.AutoMirrored.Filled.VolumeUp, feedback.fraction, stringResource(R.string.gesture_volume))
        is GestureFeedback.Brightness -> Triple(Icons.Default.BrightnessMedium, feedback.fraction, stringResource(R.string.gesture_brightness))
        is GestureFeedback.Seek -> Triple(
            if (feedback.forward) Icons.Default.FastForward else Icons.Default.FastRewind,
            null,
            stringResource(if (feedback.forward) R.string.gesture_seek_forward else R.string.gesture_seek_back),
        )
    }
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(36.dp))
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.size(width = 120.dp, height = 4.dp))
        } else {
            Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private const val FEEDBACK_HIDE_DELAY_MS = 700L
/** A drag over the full screen height changes the level by this much (1.5 = 150%). */
private const val DRAG_SENSITIVITY = 1.5f
private const val DEFAULT_BRIGHTNESS = 0.5f
/** Never fully dark, so the user can always see the screen. */
private const val MIN_BRIGHTNESS = 0.02f
