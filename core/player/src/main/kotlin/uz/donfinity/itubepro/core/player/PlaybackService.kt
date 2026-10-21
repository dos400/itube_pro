package uz.donfinity.itubepro.core.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.core.player.media.YoutubeMediaSourceFactory
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.PlaybackSource
import uz.donfinity.itubepro.domain.repository.HistoryRepository
import uz.donfinity.itubepro.domain.repository.SettingsRepository
import uz.donfinity.itubepro.domain.repository.SponsorBlockRepository

/**
 * Owns the single ExoPlayer and its MediaSession, and keeps playing with the screen off, the
 * app in the background, or the task swiped away.
 *
 * MediaSessionService does the hard parts for us. While the player is playing it starts
 * itself as a foreground service with a media notification, and it exposes the session to
 * the lock screen, Bluetooth/headset buttons, Android Auto and our own UI (MediaController).
 * Compare that with the manual notification/foreground code in
 * ref/NewPipe/.../player/PlayerService.java.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject internal lateinit var resolver: MediaItemResolver
    @Inject internal lateinit var settings: SettingsRepository
    @Inject internal lateinit var history: HistoryRepository
    @Inject internal lateinit var sponsorBlock: SponsorBlockRepository

    private var session: MediaSession? = null
    private var skipper: SponsorBlockSkipper? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(YoutubeMediaSourceFactory(this))
            // handleAudioFocus = true: pause for calls and other apps, duck for short
            // sounds like navigation prompts, resume when focus comes back.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            // Pause when headphones are unplugged or Bluetooth disconnects ("becoming noisy").
            .setHandleAudioBecomingNoisy(true)
            // Hold a partial wake lock + Wi-Fi lock while playing, so the CPU and Wi-Fi radio
            // don't sleep with the screen off. Needs the WAKE_LOCK permission.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()

        player.addListener(PlaybackRecovery(player, resolver, lifecycleScope))
        player.addListener(PlaybackHistoryTracker(player, history, lifecycleScope))
        observeAudioOnly(player)

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.playback_notification_channel)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) },
        )

        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .apply { launchAppIntent()?.let(::setSessionActivity) }
            .build()

        skipper = SponsorBlockSkipper(
            player = player,
            repository = sponsorBlock,
            settings = settings,
            scope = lifecycleScope,
            onSegmentsChanged = { mediaId, segments ->
                session?.setSessionExtras(SponsorBlockProtocol.segmentsToBundle(mediaId, segments))
            },
            onSkipped = { segment ->
                session?.broadcastCustomCommand(
                    SponsorBlockProtocol.SKIPPED_COMMAND,
                    SponsorBlockProtocol.segmentToBundle(segment),
                )
            },
        ).also { it.start() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * The user swiped the app away from Recents. Keep the service alive while playing (that's
     * the point of background playback). If paused or stopped, shut down cleanly instead of
     * leaving a dead notification behind.
     *
     * We check the player ourselves instead of using [isPlaybackOngoing]. In Media3 1.11 that
     * returns "is the service still in the foreground", and Media3 keeps it there for a grace
     * period after pausing, so a paused player would survive the swipe (verified on device).
     *
     * Plain stopSelf(), not pauseAllPlayersAndStopSelf(): the player is already not playing,
     * and the extra pause makes Media3 post a notification update that can outlive the
     * process, leaving a stuck notification (also verified on device).
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        val playing = player != null &&
            player.playWhenReady &&
            player.mediaItemCount > 0 &&
            player.playbackState != Player.STATE_ENDED
        if (!playing) stopSelf()
    }

    override fun onDestroy() {
        skipper = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /**
     * Audio only = disable the video track type. With separate video and audio sources,
     * ExoPlayer then stops fetching video segments, so toggling is instant and nothing
     * re-buffers.
     */
    private fun observeAudioOnly(player: Player) {
        lifecycleScope.launch {
            settings.audioOnly.collect { audioOnly ->
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly)
                    .build()
            }
        }
    }

    /** Tapping the notification opens the app, wherever it was. */
    private fun launchAppIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private inner class SessionCallback : MediaSession.Callback {
        /**
         * Our own UI may also send the SponsorBlock "Undo" custom command. Custom commands must
         * be listed in the connection result, or the session rejects them. Other apps keep
         * Media3's defaults.
         */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val result = super.onConnect(session, controller)
            if (controller.packageName != packageName || !result.isAccepted) return result
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailablePlayerCommands(result.availablePlayerCommands)
                .setAvailableSessionCommands(
                    result.availableSessionCommands.buildUpon().add(SponsorBlockProtocol.UNDO_COMMAND).build(),
                )
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == SponsorBlockProtocol.ACTION_UNDO) {
                SponsorBlockProtocol.segmentFromBundle(args)?.let { skipper?.undo(it.startMs) }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        /**
         * Controllers send light MediaItems (mediaId = video URL). We resolve them to playable
         * items here, asynchronously, so extraction runs in the service.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = launchFuture(mediaSession, controller) {
            mediaItems.map { resolver.resolve(it) }
        }

        /**
         * setMediaItem(s) from the UI. Same resolving as above, plus **resume**: if the UI
         * didn't ask for a specific position, start where the user stopped last time.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = launchFuture(mediaSession, controller) {
            val resolved = mediaItems.map { resolver.resolve(it) }
            val index = if (startIndex == C.INDEX_UNSET) 0 else startIndex
            val position = if (startPositionMs == C.TIME_UNSET) {
                resolved.getOrNull(index)?.let { resumePosition(it) } ?: C.TIME_UNSET
            } else {
                startPositionMs
            }
            MediaSession.MediaItemsWithStartPosition(resolved, index, position)
        }
    }

    /**
     * Saved position for [item], or TIME_UNSET (= from the start) when there's nothing useful
     * to resume: never watched, only the first seconds, almost finished, or a live stream.
     */
    private suspend fun resumePosition(item: MediaItem): Long {
        val source = item.localConfiguration?.tag as? PlaybackSource ?: return C.TIME_UNSET
        if (source.liveHls != null) return C.TIME_UNSET
        val saved = try {
            history.positionOf(item.mediaId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // history is best-effort
        } ?: return C.TIME_UNSET
        val durationMs = source.durationSeconds?.times(1000)
        val nearEnd = durationMs != null && saved > durationMs - RESUME_END_MARGIN_MS
        return if (saved < RESUME_MIN_POSITION_MS || nearEnd) C.TIME_UNSET else saved
    }

    /**
     * Runs [block] in the service's scope and exposes it as the ListenableFuture Media3
     * expects. Extraction errors go to the UI as a SessionError (shown as a snackbar).
     */
    private fun <T> launchFuture(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        block: suspend () -> T,
    ): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        lifecycleScope.launch {
            try {
                future.set(block())
            } catch (e: CancellationException) {
                future.cancel(false)
                throw e
            } catch (e: AppException) {
                session.sendError(controller, PlaybackErrors.toSessionError(e))
                future.setException(e)
            }
        }
        return future
    }

    private companion object {
        const val SEEK_INCREMENT_MS = 10_000L
        /** Don't bother resuming the first few seconds. */
        const val RESUME_MIN_POSITION_MS = 5_000L
        /** Closer than this to the end counts as finished: start over. */
        const val RESUME_END_MARGIN_MS = 10_000L
    }
}
