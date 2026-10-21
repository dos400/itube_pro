package uz.donfinity.itubepro.domain.model

/** A part of a video to skip automatically, as reported by the SponsorBlock community. */
data class SkipSegment(
    val startMs: Long,
    val endMs: Long,
    val category: SegmentCategory,
)

/** SponsorBlock categories. [apiName] is the identifier used by the API (sponsor.ajay.app). */
enum class SegmentCategory(val apiName: String) {
    SPONSOR("sponsor"),
    SELF_PROMO("selfpromo"),
    INTERACTION("interaction"),
    INTRO("intro"),
    OUTRO("outro"),
    PREVIEW("preview"),
    MUSIC_OFFTOPIC("music_offtopic"),
    FILLER("filler"),
    ;

    companion object {
        /** Categories most people want skipped. The others cut into actual content. */
        val DEFAULTS: Set<SegmentCategory> = setOf(SPONSOR, SELF_PROMO, INTERACTION)

        fun fromApiName(name: String): SegmentCategory? = entries.firstOrNull { it.apiName == name }
    }
}

/** Pure helpers for segment lists. No I/O, so they're easy to unit test. */
object SkipSegments {

    /** Segments closer than this are treated as one: two skips in a row feel broken. */
    const val MERGE_GAP_MS = 1_000L

    /**
     * Sorts and merges overlapping or back-to-back segments, so the player jumps once to the
     * end of the whole chain. Same idea as getChainSkipEndTime in ref/Tubular/.../player/Player.java.
     * The merged segment keeps the category of the first one.
     */
    fun merge(segments: List<SkipSegment>): List<SkipSegment> {
        val result = mutableListOf<SkipSegment>()
        for (segment in segments.filter { it.endMs > it.startMs }.sortedBy { it.startMs }) {
            val last = result.lastOrNull()
            if (last != null && segment.startMs <= last.endMs + MERGE_GAP_MS) {
                result[result.lastIndex] = last.copy(endMs = maxOf(last.endMs, segment.endMs))
            } else {
                result += segment
            }
        }
        return result
    }
}
