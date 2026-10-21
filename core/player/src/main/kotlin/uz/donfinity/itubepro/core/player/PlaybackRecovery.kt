package uz.donfinity.itubepro.core.player

import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.core.player.media.isHttpForbidden
import uz.donfinity.itubepro.domain.error.AppException

/**
 * Recovers from the errors background playback actually hits:
 *
 * 1. **HTTP 403.** Stream URLs expire after about 6 h and contain `ip=<your IP>`, so they
 *    also break when the phone switches between Wi-Fi and mobile data. Fix: extract again
 *    (bypassing the cache), swap in the new item, seek back to where we were, and resume.
 *    A cooldown per video stops a loop if fresh URLs fail too.
 * 2. **Network failures** (connection lost/timeout) after ExoPlayer's own retries gave up.
 *    Fix: re-prepare a few times with growing delays.
 * 3. **Behind live window.** A paused live stream falls out of the server's buffer.
 *    Fix: jump back to the live edge (same as ref/NewPipe Player.java).
 *
 * Inspired by ref/NewPipe/.../player/Player.java onPlayerError (setRecovery +
 * reloadPlayQueueManager), but limited to the errors where retrying can help.
 */
@OptIn(UnstableApi::class)
internal class PlaybackRecovery(
    private val player: Player,
    private val resolver: MediaItemResolver,
    private val scope: CoroutineScope,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : Player.Listener {

    private val lastRefreshAt = mutableMapOf<String, Long>()
    private var networkRetries = 0
    private var job: Job? = null

    override fun onPlayerError(error: PlaybackException) {
        val item = player.currentMediaItem ?: return
        when {
            error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                player.seekToDefaultPosition()
                player.prepare()
            }
            error.isHttpForbidden() -> refreshStreams(player.currentMediaItemIndex, item)
            error.errorCode in RETRYABLE_NETWORK_ERRORS && networkRetries < MAX_NETWORK_RETRIES -> retryLater()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) networkRetries = 0
    }

    private fun refreshStreams(index: Int, item: MediaItem) {
        val now = clock()
        val last = lastRefreshAt[item.mediaId]
        if (last != null && now - last < REFRESH_COOLDOWN_MS) {
            Log.w(TAG, "403 again right after refreshing ${item.mediaId}; giving up")
            return
        }
        lastRefreshAt[item.mediaId] = now
        // After an error the player keeps its position, so we can resume exactly there.
        val position = player.currentPosition
        job?.cancel()
        job = scope.launch {
            val fresh = try {
                resolver.resolve(item, forceRefresh = true)
            } catch (e: AppException) {
                Log.w(TAG, "Re-extraction failed", e)
                return@launch
            }
            // The user may have started another video while we were extracting.
            if (player.currentMediaItem?.mediaId != item.mediaId) return@launch
            Log.i(TAG, "Refreshed expired stream URLs for ${item.mediaId} at ${position}ms")
            player.replaceMediaItem(index, fresh)
            player.seekTo(index, position)
            player.prepare()
        }
    }

    private fun retryLater() {
        networkRetries++
        job?.cancel()
        job = scope.launch {
            delay(NETWORK_RETRY_BASE_DELAY_MS * networkRetries)
            player.prepare()
        }
    }

    private companion object {
        const val TAG = "PlaybackRecovery"
        const val REFRESH_COOLDOWN_MS = 10_000L
        const val MAX_NETWORK_RETRIES = 3
        const val NETWORK_RETRY_BASE_DELAY_MS = 3_000L
        val RETRYABLE_NETWORK_ERRORS = setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_TIMEOUT,
        )
    }
}
