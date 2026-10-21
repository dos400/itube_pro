package uz.donfinity.itubepro.domain.model

import java.time.Instant

/** One row of watch history: the video, when it was last watched and where playback stopped. */
data class HistoryItem(
    val video: VideoSummary,
    val watchedAt: Instant,
    val positionMs: Long,
)

data class Playlist(
    val id: Long,
    val name: String,
    val itemCount: Int,
    /** Thumbnail of the first video, used as the playlist cover. */
    val thumbnailUrl: String?,
)

data class PlaylistWithItems(
    val playlist: Playlist,
    val items: List<VideoSummary>,
)

data class Subscription(
    val channelUrl: String,
    val name: String,
    val avatarUrl: String?,
)

/** A video in the subscription feed. [uploadedAt] is used for sorting; may be unknown. */
data class FeedItem(
    val video: VideoSummary,
    val uploadedAt: Instant?,
)

data class FeedRefreshResult(
    val refreshedChannels: Int,
    val failedChannels: Int,
)

/** What a list row needs to show a video from the detail screen (favorites, playlists). */
fun VideoDetails.toSummary() = VideoSummary(
    url = url,
    title = title,
    uploaderName = uploaderName,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    viewCount = viewCount,
    uploadedText = null,
    isLive = isLive,
)
