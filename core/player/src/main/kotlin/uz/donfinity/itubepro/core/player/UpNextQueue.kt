package uz.donfinity.itubepro.core.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.domain.model.PlaybackSource
import uz.donfinity.itubepro.domain.model.VideoSummary
import uz.donfinity.itubepro.domain.repository.VideoRepository

/**
 * Keeps one "up next" video queued after the current one, so the notification, lock screen
 * and Bluetooth get a working Next button (Media3 only shows it when there is a next item)
 * and playback can continue automatically.
 *
 * Same rule as ref/NewPipe/.../player/helper/PlayerHelper.java (autoQueueOf): the first
 * related video that isn't already in the queue. The current video's details are already in
 * the StreamInfoCache, so finding the related list costs no extra network request; only the
 * next video itself is extracted.
 */
internal class UpNextQueue(
    private val player: Player,
    private val videos: VideoRepository,
    private val resolver: MediaItemResolver,
    private val scope: CoroutineScope,
) : Player.Listener {

    private var job: Job? = null

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        trimHistory()
        ensureNext()
    }

    private fun ensureNext() {
        if (player.hasNextMediaItem()) return
        val current = player.currentMediaItem ?: return
        // Live streams have no meaningful "next" in their related list.
        if ((current.localConfiguration?.tag as? PlaybackSource)?.liveHls != null) return

        job?.cancel()
        job = scope.launch {
            try {
                val queued = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
                val next = videos.getDetails(current.mediaId).related
                    .firstOrNull { it.url !in queued && !it.isLive }
                    ?: return@launch
                val resolved = resolver.resolve(next.toMediaItem())
                // The user may have started something else meanwhile.
                if (player.currentMediaItem?.mediaId != current.mediaId || player.hasNextMediaItem()) return@launch
                Log.i(TAG, "Up next: ${next.title}")
                player.addMediaItem(resolved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // No "next" this time; playback of the current video is unaffected.
                Log.w(TAG, "Couldn't queue the next video", e)
            }
        }
    }

    /** Keep at most [MAX_HISTORY] played items before the current one (for Previous). */
    private fun trimHistory() {
        val extra = player.currentMediaItemIndex - MAX_HISTORY
        if (extra > 0) player.removeMediaItems(0, extra)
    }

    private fun VideoSummary.toMediaItem() = MediaItem.Builder()
        .setMediaId(url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(uploaderName)
                .setArtworkUri(thumbnailUrl?.let(Uri::parse))
                .build(),
        )
        .build()

    private companion object {
        const val TAG = "UpNextQueue"
        const val MAX_HISTORY = 20
    }
}
