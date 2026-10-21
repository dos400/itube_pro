package uz.donfinity.itubepro.domain.usecase

import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.RecommendationMixer
import uz.donfinity.itubepro.domain.model.Recommendations
import uz.donfinity.itubepro.domain.model.VideoSummary
import uz.donfinity.itubepro.domain.repository.FavoritesRepository
import uz.donfinity.itubepro.domain.repository.HistoryRepository
import uz.donfinity.itubepro.domain.repository.SubscriptionRepository
import uz.donfinity.itubepro.domain.repository.TrendingRepository
import uz.donfinity.itubepro.domain.repository.VideoRepository

/**
 * "For you" without a Google account: everything is worked out on the phone.
 *
 * Seeds = the last few watched videos plus a random pick from older history and favorites
 * (so each refresh shows something different). YouTube's related videos for each seed are the
 * candidates; they come from the same StreamInfo the detail screen uses, so recently watched
 * seeds are usually already cached. Already watched videos are left out. New uploads from
 * subscriptions are mixed in. With no history at all, fall back to trending.
 */
class GetRecommendationsUseCase(
    private val history: HistoryRepository,
    private val favorites: FavoritesRepository,
    private val subscriptions: SubscriptionRepository,
    private val videos: VideoRepository,
    private val trending: TrendingRepository,
    private val random: Random = Random.Default,
) {

    /** @throws AppException only when there's nothing at all to show. */
    suspend operator fun invoke(): Recommendations = coroutineScope {
        val watched = history.observeHistory().first().map { it.video }
        val seeds = pickSeeds(watched, favorites.observeFavorites().first())
        val watchedUrls = watched.mapTo(mutableSetOf()) { it.url }

        val results = seeds.map { seed ->
            async {
                // One unavailable seed (deleted video, a network blip) shouldn't empty the
                // whole home screen, so failures are collected instead of thrown.
                try {
                    Result.success(videos.getDetails(seed.url).related)
                } catch (e: AppException) {
                    Result.failure(e)
                }
            }
        }.awaitAll()
        val related = results.map { it.getOrDefault(emptyList()) }
        val firstError = results.firstNotNullOfOrNull { it.exceptionOrNull() as? AppException }
        val fresh = subscriptions.observeFeed().first().take(FRESH_FEED_COUNT).map { it.video }

        val items = RecommendationMixer.mix(related, fresh, exclude = watchedUrls)
        if (items.isNotEmpty()) return@coroutineScope Recommendations(items, Recommendations.Source.PERSONAL)

        try {
            Recommendations(trending.trending(), Recommendations.Source.TRENDING)
        } catch (e: AppException) {
            throw firstError ?: e
        }
    }

    private fun pickSeeds(watched: List<VideoSummary>, liked: List<VideoSummary>): List<VideoSummary> {
        val recent = watched.take(RECENT_SEEDS)
        val older = (watched.drop(RECENT_SEEDS).take(OLDER_HISTORY_WINDOW) + liked)
            .distinctBy { it.url }
            .filter { candidate -> recent.none { it.url == candidate.url } }
            .shuffled(random)
            .take(RANDOM_SEEDS)
        return (recent + older).filterNot { it.isLive }
    }

    private companion object {
        const val RECENT_SEEDS = 3
        const val RANDOM_SEEDS = 3
        const val OLDER_HISTORY_WINDOW = 30
        const val FRESH_FEED_COUNT = 15
    }
}
