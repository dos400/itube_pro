package uz.donfinity.itubepro.core.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import javax.inject.Inject
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.PlaybackSource
import uz.donfinity.itubepro.domain.repository.PlaybackRepository

/**
 * Turns a "light" MediaItem (mediaId = video URL, plus display metadata) into a playable one
 * that carries the resolved [PlaybackSource] as its tag.
 *
 * The UI only sends light items. Resolving happens here in the service, so it also works
 * when there is no UI, e.g. re-extracting after a 403 while the screen is off.
 *
 * Note: MediaItem tags are not sent across processes, so a controller never sees them.
 * That's fine, because the tag is only for our MediaSource.Factory inside this process.
 */
internal class MediaItemResolver @Inject constructor(
    private val repository: PlaybackRepository,
) {
    /** @throws AppException */
    suspend fun resolve(item: MediaItem, forceRefresh: Boolean = false): MediaItem {
        val source = repository.resolve(item.mediaId, forceRefresh)
        val metadata = if (item.mediaMetadata.title != null) item.mediaMetadata else source.toMetadata()
        return item.buildUpon()
            .setUri(source.videoUrl)
            .setTag(source)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun PlaybackSource.toMetadata() = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(uploaderName)
        .setArtworkUri(thumbnailUrl?.let(Uri::parse))
        .build()
}
