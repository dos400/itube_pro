package uz.donfinity.itubepro.core.player

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.domain.model.PlaybackSource
import uz.donfinity.itubepro.domain.model.VideoSummary
import uz.donfinity.itubepro.domain.repository.HistoryRepository

/**
 * Writes watch history and resume positions from inside the service, so it also works with
 * the screen off and no UI. Idea from ref/NewPipe/.../player/Player.java
 * (saveStreamProgressState), driven by Player.Listener events here.
 *
 * - First time an item starts playing → add/move it to the top of history.
 * - While playing → save the position every [SAVE_INTERVAL_MS], and on every pause.
 * - Played to the end → position 0, so it starts from the beginning next time.
 */
internal class PlaybackHistoryTracker(
    private val player: Player,
    private val history: HistoryRepository,
    private val scope: CoroutineScope,
) : Player.Listener {

    private var recordedMediaId: String? = null
    private var ticker: Job? = null

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            recordIfNew()
            ticker?.cancel()
            ticker = scope.launch {
                while (isActive) {
                    delay(SAVE_INTERVAL_MS)
                    savePosition()
                }
            }
        } else {
            ticker?.cancel()
            savePosition()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        recordedMediaId = null
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            val mediaId = player.currentMediaItem?.mediaId ?: return
            write { history.savePosition(mediaId, 0) }
        }
    }

    private fun recordIfNew() {
        val item = player.currentMediaItem ?: return
        if (recordedMediaId == item.mediaId) return
        recordedMediaId = item.mediaId
        val video = item.toSummary()
        write { history.recordWatch(video) }
    }

    private fun savePosition() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        // A live stream has no position worth resuming.
        if (player.isCurrentMediaItemLive || player.playbackState == Player.STATE_ENDED) return
        val position = player.currentPosition
        write { history.savePosition(mediaId, position) }
    }

    /** History is best-effort: a database problem must never crash playback. */
    private fun write(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Saving history failed", e)
            }
        }
    }

    /** Prefer the resolved source (always complete); fall back to the item's display metadata. */
    private fun MediaItem.toSummary(): VideoSummary {
        val source = localConfiguration?.tag as? PlaybackSource
        val durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        return VideoSummary(
            url = mediaId,
            title = source?.title ?: mediaMetadata.title?.toString().orEmpty(),
            uploaderName = source?.uploaderName ?: mediaMetadata.artist?.toString().orEmpty(),
            thumbnailUrl = source?.thumbnailUrl ?: mediaMetadata.artworkUri?.toString(),
            durationSeconds = source?.durationSeconds ?: durationMs?.div(1000),
            viewCount = null,
            uploadedText = null,
            isLive = source?.liveHls != null,
        )
    }

    private companion object {
        const val TAG = "PlaybackHistory"
        const val SAVE_INTERVAL_MS = 15_000L
    }
}
