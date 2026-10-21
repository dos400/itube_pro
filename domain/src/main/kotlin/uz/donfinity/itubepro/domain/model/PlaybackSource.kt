package uz.donfinity.itubepro.domain.model

/**
 * Everything the player needs to start a video, already resolved by the data layer.
 * Stream URLs expire (and are bound to the device's IP), so the player asks for a fresh
 * one when it gets HTTP 403.
 */
data class PlaybackSource(
    val videoUrl: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    /** Video track, or a muxed video+audio stream when [audio] is null. Null for audio-only content. */
    val video: PlaybackTrack?,
    val audio: PlaybackTrack?,
)

data class PlaybackTrack(
    val url: String,
    /**
     * A DASH manifest (XML) describing this single stream, or null to play [url] as a plain
     * progressive file. A manifest lets the player fetch small segments, which seeks
     * faster and avoids YouTube throttling large byte ranges.
     */
    val dashManifest: String?,
    /** Extra HTTP headers this stream's server expects (User-Agent, Origin, ...). */
    val httpHeaders: Map<String, String>,
    /** Human-readable description for logs/debugging, e.g. "720p avc1". */
    val label: String,
)
