package uz.donfinity.itubepro.domain.model

/**
 * A video together with every stream the extractor found for it.
 *
 * Stream URLs expire after a few hours. Hold on to this object only for the current
 * playback session, never persist it.
 */
data class ExtractedVideo(
    val id: String,
    val url: String,
    val title: String,
    val uploaderName: String,
    val durationSeconds: Long,
    /** URL of a server-provided DASH manifest, or null when YouTube didn't return one. */
    val dashManifestUrl: String?,
    /** URL of an HLS manifest (usually only present for live streams). */
    val hlsManifestUrl: String?,
    val streams: List<MediaStream>,
)

data class MediaStream(
    val kind: Kind,
    val delivery: Delivery,
    /** A direct URL when [delivery] is [Delivery.PROGRESSIVE], otherwise manifest content or URL. */
    val content: String,
    /** e.g. "MPEG-4", "WebM", "m4a". Null if the extractor didn't know the container. */
    val format: String?,
    val codec: String?,
    /** e.g. "1080p60" for video or "128 kbps" style label for audio; may be empty. */
    val qualityLabel: String,
    val bitrate: Int,
    val height: Int,
    val audioTrackName: String?,
) {
    enum class Kind {
        /** Video and audio in one file (legacy YouTube itags, max 360p). */
        MUXED,
        /** Video track only, must be merged with an [AUDIO] stream for sound. */
        VIDEO_ONLY,
        AUDIO,
    }

    enum class Delivery { PROGRESSIVE, DASH, HLS, OTHER }
}
