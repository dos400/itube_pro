package uz.donfinity.itubepro.domain.model

/** What the home screen shows. [source] decides the heading ("For you" or "Trending"). */
data class Recommendations(
    val items: List<VideoSummary>,
    val source: Source,
) {
    enum class Source {
        /** Built from this phone's history, favorites and subscriptions. */
        PERSONAL,

        /** Nothing to go on yet (fresh install), so YouTube's public charts. */
        TRENDING,
    }
}

/**
 * Pure ranking for the home screen, kept apart from the use case so it's easy to test.
 *
 * Related-video lists come in seed order (most relevant interest first). Taking position 0 of
 * every list, then position 1, and so on gives each interest a share near the top instead of
 * letting the first seed fill the screen. Every [freshEvery]-th slot goes to a new video from
 * a subscribed channel.
 */
object RecommendationMixer {

    fun mix(
        related: List<List<VideoSummary>>,
        fresh: List<VideoSummary>,
        exclude: Set<String>,
        limit: Int = 60,
        maxPerChannel: Int = 3,
        freshEvery: Int = 4,
    ): List<VideoSummary> {
        val result = mutableListOf<VideoSummary>()
        val seen = exclude.toMutableSet()
        val perChannel = mutableMapOf<String, Int>()

        fun tryAdd(video: VideoSummary): Boolean {
            if (video.url in seen) return false
            val count = perChannel[video.uploaderName] ?: 0
            if (video.uploaderName.isNotEmpty() && count >= maxPerChannel) return false
            seen += video.url
            perChannel[video.uploaderName] = count + 1
            result += video
            return true
        }

        val freshQueue = ArrayDeque(fresh)
        fun addFresh() {
            while (freshQueue.isNotEmpty()) if (tryAdd(freshQueue.removeFirst())) return
        }

        val depth = related.maxOfOrNull { it.size } ?: 0
        outer@ for (i in 0 until depth) {
            for (list in related) {
                if (result.size >= limit) break@outer
                val video = list.getOrNull(i) ?: continue
                if (tryAdd(video) && result.size % freshEvery == freshEvery - 1) addFresh()
            }
        }
        // Few or no seeds: let the subscription videos fill the rest.
        while (result.size < limit && freshQueue.isNotEmpty()) addFresh()
        return result.take(limit)
    }
}
