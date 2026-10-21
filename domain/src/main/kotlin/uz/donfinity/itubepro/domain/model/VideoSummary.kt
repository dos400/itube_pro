package uz.donfinity.itubepro.domain.model

/** A video as it appears in a list (search results, related videos). */
data class VideoSummary(
    /** Canonical video URL; used as the video's identity across the app. */
    val url: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String?,
    /** Null for live streams or when unknown. */
    val durationSeconds: Long?,
    /** Null when YouTube didn't report it. */
    val viewCount: Long?,
    /** Already localised by YouTube, e.g. "3 years ago". */
    val uploadedText: String?,
    val isLive: Boolean,
)

/**
 * Opaque cursor for the next page of a list. Only the data layer knows what's inside.
 * The UI just hands it back to request the next page.
 */
@JvmInline
value class PageToken(val value: String)

data class SearchPage(
    val items: List<VideoSummary>,
    /** Null when there are no more pages. */
    val nextPage: PageToken?,
    /** "Did you mean ..." text, or the corrected query when [isCorrectedSearch] is true. */
    val suggestion: String?,
    /** True when YouTube searched for [suggestion] instead of the typed query. */
    val isCorrectedSearch: Boolean,
)
