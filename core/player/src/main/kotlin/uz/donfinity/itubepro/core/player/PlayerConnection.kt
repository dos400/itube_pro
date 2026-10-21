package uz.donfinity.itubepro.core.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.SkipSegment

/** One selectable video quality of the current item. */
data class VideoQuality(
    val height: Int,
    val frameRate: Float,
    internal val trackGroup: TrackGroup,
    internal val trackIndex: Int,
) {
    /** "720p", or "720p60" for high frame rate. */
    val label: String get() = if (frameRate > 30f) "${height}p${frameRate.roundToInt()}" else "${height}p"
}

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
    val isLive: Boolean = false,
    /** True when the current item has a video track selected (false in audio-only mode). */
    val hasVideo: Boolean = false,
    /** Size of the video being rendered right now, 0 when unknown (used for PiP aspect ratio). */
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    /** Qualities the user can pick, lowest first. Empty when there's nothing to choose. */
    val qualities: List<VideoQuality> = emptyList(),
    /** The user's manual pick, or null for "Auto" (adaptive). */
    val selectedQuality: VideoQuality? = null,
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

    private val _segments = MutableStateFlow<Pair<String?, List<SkipSegment>>>(null to emptyList())
    /** SponsorBlock segments of the current item, as (mediaId, segments). For seek bar markers. */
    val segments: StateFlow<Pair<String?, List<SkipSegment>>> = _segments.asStateFlow()

    private val _skips = MutableSharedFlow<SkipSegment>(extraBufferCapacity = 1)
    /** One-off: the service just skipped this segment (shown as a snackbar with Undo). */
    val skips: SharedFlow<SkipSegment> = _skips.asSharedFlow()

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

                override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                    _segments.value = SponsorBlockProtocol.segmentsFromBundle(extras)
                }

                override fun onCustomCommand(
                    controller: MediaController,
                    command: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (command.customAction == SponsorBlockProtocol.ACTION_SKIPPED) {
                        SponsorBlockProtocol.segmentFromBundle(args)?.let(_skips::tryEmit)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
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
            // Extras set before we connected aren't re-sent as a change; read them once.
            _segments.value = SponsorBlockProtocol.segmentsFromBundle(controller.sessionExtras)
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

    /** Position as a 0..1 fraction of the duration, or null if unknown (e.g. live). For progress bars. */
    @MainThread
    fun progress(): Float? {
        val controller = _controller.value ?: return null
        val duration = controller.duration
        if (duration == C.TIME_UNSET || duration <= 0 || controller.isCurrentMediaItemLive) return null
        return (controller.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
    }

    /**
     * Picks a fixed quality, or `null` for Auto. All qualities live in one track group, so this
     * is only a track-selection change: the player switches at the next segment, with no
     * re-buffering. Compare ref/NewPipe, which rebuilds the whole media source.
     */
    @MainThread
    fun selectQuality(quality: VideoQuality?) {
        val controller = _controller.value ?: return
        controller.trackSelectionParameters = controller.trackSelectionParameters.buildUpon()
            .apply {
                if (quality == null) {
                    clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                } else {
                    setOverrideForType(TrackSelectionOverride(quality.trackGroup, quality.trackIndex))
                }
            }
            .build()
    }

    /** Seeks back into a skipped segment and lets it play this once. */
    @MainThread
    fun undoSkip(segment: SkipSegment) {
        _controller.value?.sendCustomCommand(SponsorBlockProtocol.UNDO_COMMAND, SponsorBlockProtocol.segmentToBundle(segment))
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
        val (qualities, selected) = videoQualities()
        return NowPlayingState(
            mediaId = item.mediaId,
            title = mediaMetadata.title?.toString(),
            artist = mediaMetadata.artist?.toString(),
            artworkUri = mediaMetadata.artworkUri,
            isPlaying = isPlaying,
            isBuffering = playbackState == Player.STATE_BUFFERING,
            hasError = playerError != null,
            isLive = isCurrentMediaItemLive,
            hasVideo = currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO),
            videoWidth = videoSize.width,
            videoHeight = videoSize.height,
            qualities = qualities,
            selectedQuality = selected,
        )
    }

    /** Reads the video track group and any manual override from the current tracks. */
    private fun Player.videoQualities(): Pair<List<VideoQuality>, VideoQuality?> {
        val group = currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO } ?: return emptyList<VideoQuality>() to null
        val qualities = (0 until group.length)
            .filter { group.isTrackSupported(it) }
            .map { i ->
                val format = group.getTrackFormat(i)
                VideoQuality(format.height, format.frameRate, group.mediaTrackGroup, i)
            }
            .sortedWith(compareBy<VideoQuality> { it.height }.thenBy { it.frameRate })
        val override = trackSelectionParameters.overrides[group.mediaTrackGroup]
        val selected = override?.trackIndices?.singleOrNull()?.let { index -> qualities.firstOrNull { it.trackIndex == index } }
        // A single quality (e.g. a muxed fallback) is nothing to choose from.
        return (if (qualities.size > 1) qualities else emptyList()) to selected
    }
}
