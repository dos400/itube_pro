package uz.donfinity.itubepro.core.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.donfinity.itubepro.domain.error.AppException

/** What the UI shows about the current playback. */
data class NowPlayingState(
    /** The video URL of the current item, or null when nothing is loaded. */
    val mediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val artworkUri: Uri? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /** The player stopped with an error (e.g. 403 even after re-extraction). */
    val hasError: Boolean = false,
)

/**
 * The UI's only way to reach the player: a [MediaController] connected to [PlaybackService].
 * The controller works the same whether the service runs in our process or not, and the
 * service keeps playing when the controller (UI) goes away.
 *
 * Lifecycle: call [connect] in Activity.onStart and [disconnect] in onStop. A connected
 * controller keeps the service bound, so holding it while in the background would stop
 * Android from ever destroying a paused service.
 */
@Singleton
class PlayerConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingItem: MediaItem? = null

    private val _controller = MutableStateFlow<MediaController?>(null)
    /** For binding a PlayerView. Null while disconnected. */
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<AppException>(extraBufferCapacity = 1)
    /** One-off errors from the service, e.g. extraction failed when starting a video. */
    val errors: SharedFlow<AppException> = _errors.asSharedFlow()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _state.value = player.toState()
        }
    }

    // MediaController.Listener.onError(SessionError) is @UnstableApi.
    @OptIn(UnstableApi::class)
    @MainThread
    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onError(controller: MediaController, sessionError: SessionError) {
                    _errors.tryEmit(PlaybackErrors.fromSessionError(sessionError))
                }
            })
            .buildAsync()
        controllerFuture = future
        future.addListener({
            // future.get() throws if the connection failed, or after disconnect() cancelled it.
            val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller.addListener(playerListener)
            _controller.value = controller
            _state.value = controller.toState()
            pendingItem?.let { start(controller, it) }
            pendingItem = null
        }, ContextCompat.getMainExecutor(context))
    }

    @MainThread
    fun disconnect() {
        _controller.value?.removeListener(playerListener)
        _controller.value = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
    }

    /** Starts playing a video. Only metadata travels; the service extracts the streams. */
    @MainThread
    fun playVideo(videoUrl: String, title: String, artist: String, artworkUrl: String?) {
        val item = MediaItem.Builder()
            .setMediaId(videoUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artworkUrl?.let(Uri::parse))
                    .build(),
            )
            .build()
        val controller = _controller.value
        if (controller == null) {
            pendingItem = item
            connect()
        } else {
            start(controller, item)
        }
    }

    /** Play/pause, which also restarts after the end and re-prepares after an error. */
    @OptIn(UnstableApi::class)
    @MainThread
    fun togglePlayPause() {
        _controller.value?.let { Util.handlePlayPauseButtonAction(it) }
    }

    /** Clears the queue. The service then stops itself and the notification goes away. */
    @MainThread
    fun stop() {
        _controller.value?.run {
            stop()
            clearMediaItems()
        }
    }

    private fun start(controller: MediaController, item: MediaItem) {
        controller.setMediaItem(item)
        controller.prepare()
        controller.play()
    }

    private fun Player.toState(): NowPlayingState {
        val item = currentMediaItem ?: return NowPlayingState()
        return NowPlayingState(
            mediaId = item.mediaId,
            title = mediaMetadata.title?.toString(),
            artist = mediaMetadata.artist?.toString(),
            artworkUri = mediaMetadata.artworkUri,
            isPlaying = isPlaying,
            isBuffering = playbackState == Player.STATE_BUFFERING,
            hasError = playerError != null,
        )
    }
}
