package uz.donfinity.itubepro.ui.player

import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.donfinity.itubepro.core.player.NowPlayingState

/**
 * True while the app is in Picture-in-Picture. The shell provides it; screens read it to
 * hide their own video surfaces, so only the PiP surface is attached to the player.
 */
val LocalInPictureInPicture = compositionLocalOf { false }

@Composable
fun rememberIsInPictureInPictureMode(): Boolean {
    val activity = LocalActivity.current as? ComponentActivity ?: return false
    var inPip by remember { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

/**
 * Where the main video currently is on screen, in window coordinates. Android uses it to
 * animate the video smoothly into the PiP window instead of shrinking the whole app.
 */
object PipSourceRect {
    private val _bounds = MutableStateFlow<Rect?>(null)
    val bounds: StateFlow<Rect?> = _bounds.asStateFlow()

    fun update(rect: Rect?) {
        _bounds.value = rect
    }
}

/** PiP is offered only while a video (not audio-only) is actually playing. */
fun NowPlayingState.isPipEligible(): Boolean = isPlaying && hasVideo

/**
 * @param autoEnter Android 12+: enter PiP by itself when the user goes Home. Older versions
 *   need an explicit enterPictureInPictureMode() call from onUserLeaveHint.
 */
fun NowPlayingState.toPipParams(autoEnter: Boolean, sourceRect: Rect?): PictureInPictureParams =
    PictureInPictureParams.Builder()
        .setAspectRatio(pipAspectRatio(videoWidth, videoHeight))
        .setSourceRectHint(sourceRect)
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setAutoEnterEnabled(autoEnter)
                // Video content: let the system resize smoothly instead of cross-fading.
                setSeamlessResizeEnabled(true)
            }
        }
        .build()

/** Android throws if the ratio is outside 1:2.39 … 2.39:1, so clamp it. */
private fun pipAspectRatio(width: Int, height: Int): Rational {
    if (width <= 0 || height <= 0) return Rational(16, 9)
    val ratio = width.toFloat() / height
    return when {
        ratio > MAX_RATIO -> Rational(239, 100)
        ratio < 1 / MAX_RATIO -> Rational(100, 239)
        else -> Rational(width, height)
    }
}

private const val MAX_RATIO = 2.39f
