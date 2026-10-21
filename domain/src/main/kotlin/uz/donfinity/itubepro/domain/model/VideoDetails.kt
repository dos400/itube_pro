package uz.donfinity.itubepro.domain.model

import java.time.Instant

/** Everything the video detail screen shows. Playback streams live in [ExtractedVideo]. */
data class VideoDetails(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val uploaderName: String,
    val uploaderAvatarUrl: String?,
    val uploaderVerified: Boolean,
    /** Null when hidden or unknown. */
    val subscriberCount: Long?,
    val viewCount: Long?,
    val likeCount: Long?,
    val uploadDate: Instant?,
    val durationSeconds: Long?,
    val isLive: Boolean,
    val description: Description,
    val related: List<VideoSummary>,
)

data class Description(val content: String, val format: Format) {
    enum class Format { HTML, MARKDOWN, PLAIN }
}
