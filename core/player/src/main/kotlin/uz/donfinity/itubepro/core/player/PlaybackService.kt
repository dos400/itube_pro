package uz.donfinity.itubepro.core.player

import android.app.PendingIntent
import android.content.Intent
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
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.core.player.media.YoutubeMediaSourceFactory
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.repository.SettingsRepository

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

    private var session: MediaSession? = null

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
         * Controllers send light MediaItems (mediaId = video URL). We resolve them to playable
         * items here, asynchronously. Media3's default onSetMediaItems also routes through
         * this method, so setMediaItem() from the UI ends up here too.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            val future = SettableFuture.create<List<MediaItem>>()
            lifecycleScope.launch {
                try {
                    future.set(mediaItems.map { resolver.resolve(it) })
                } catch (e: CancellationException) {
                    future.cancel(false)
                    throw e
                } catch (e: AppException) {
                    // Tell the UI why nothing is playing (shown as a snackbar).
                    mediaSession.sendError(controller, PlaybackErrors.toSessionError(e))
                    future.setException(e)
                }
            }
            return future
        }
    }

    private companion object {
        const val SEEK_INCREMENT_MS = 10_000L
    }
}
