package uz.donfinity.itubepro.data.repository

import android.util.Log
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import uz.donfinity.itubepro.data.db.AppDatabase
import uz.donfinity.itubepro.data.db.FeedItemEntity
import uz.donfinity.itubepro.data.db.FeedStateEntity
import uz.donfinity.itubepro.data.db.SubscriptionEntity
import uz.donfinity.itubepro.data.di.IoDispatcher
import uz.donfinity.itubepro.data.error.toAppException
import uz.donfinity.itubepro.data.mapper.isLive
import uz.donfinity.itubepro.data.mapper.preferredUrl
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.FeedItem
import uz.donfinity.itubepro.domain.model.FeedRefreshResult
import uz.donfinity.itubepro.domain.model.Subscription
import uz.donfinity.itubepro.domain.model.VideoSummary
import uz.donfinity.itubepro.domain.repository.SubscriptionRepository

/**
 * Local subscriptions (Room) plus feed refresh over the network (NewPipe).
 *
 * Feed strategy is ref/NewPipe's "fast mode" (local/feed/service/FeedLoadManager.kt):
 * `FeedInfo` reads the channel's RSS feed, which gives about the 15 newest videos in one small
 * request. Loading full channel pages would be several heavier requests per channel.
 */
class NewPipeSubscriptionRepository @Inject constructor(
    private val db: AppDatabase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SubscriptionRepository {

    private val subscriptions = db.subscriptionDao()
    private val feed = db.feedDao()
    private val youtube = ServiceList.YouTube

    override fun observeSubscriptions(): Flow<List<Subscription>> =
        subscriptions.observe().map { list -> list.map { Subscription(it.channelUrl, it.name, it.avatarUrl) } }

    override fun isSubscribed(channelUrl: String): Flow<Boolean> =
        subscriptions.isSubscribed(channelUrl).distinctUntilChanged()

    override suspend fun subscribe(subscription: Subscription) {
        subscriptions.upsert(
            SubscriptionEntity(subscription.channelUrl, subscription.name, subscription.avatarUrl, Instant.now()),
        )
    }

    // The feed_items foreign key has ON DELETE CASCADE, so the channel's feed goes too.
    override suspend fun unsubscribe(channelUrl: String) = subscriptions.delete(channelUrl)

    override fun observeFeed(): Flow<List<FeedItem>> = feed.observe().map { rows ->
        rows.map { row ->
            FeedItem(
                video = VideoSummary(
                    url = row.videoUrl,
                    title = row.title,
                    uploaderName = row.uploaderName,
                    thumbnailUrl = row.thumbnailUrl,
                    durationSeconds = row.durationSeconds,
                    viewCount = row.viewCount,
                    uploadedText = null,
                    isLive = row.isLive,
                ),
                uploadedAt = row.uploadedAt,
            )
        }
    }

    override fun observeLastFeedRefresh(): Flow<Instant?> = feed.observeLastRefresh()

    override suspend fun refreshFeed(): FeedRefreshResult = coroutineScope {
        val channels = subscriptions.all()
        // At most PARALLEL_CHANNELS requests at once: fast, without hammering YouTube (which
        // rate-limits) or the phone's radio. Same idea as NewPipe's PARALLEL_EXTRACTIONS.
        val permits = Semaphore(PARALLEL_CHANNELS)
        val outcomes = channels.map { channel ->
            async { permits.withPermit { refreshChannel(channel) } }
        }.awaitAll()

        val failures = outcomes.filterNotNull()
        // Every channel failed with the same kind of general error (e.g. offline): report it
        // as an error instead of "0 refreshed".
        if (channels.isNotEmpty() && failures.size == channels.size) throw failures.first()

        feed.setState(FeedStateEntity(lastRefreshAt = Instant.now()))
        FeedRefreshResult(refreshedChannels = channels.size - failures.size, failedChannels = failures.size)
    }

    /** @return null on success, or the error. Errors are returned, not thrown, so one bad channel can't cancel the others. */
    private suspend fun refreshChannel(channel: SubscriptionEntity): AppException? = try {
        val items = withContext(ioDispatcher) {
            FeedInfo.getInfo(youtube, channel.channelUrl).relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { it.toFeedEntity(channel) }
        }
        feed.replaceChannel(channel.channelUrl, items)
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Feed refresh failed for ${channel.name}", e)
        e.toAppException()
    }

    private fun StreamInfoItem.toFeedEntity(channel: SubscriptionEntity): FeedItemEntity {
        val live = streamType.isLive()
        return FeedItemEntity(
            videoUrl = url,
            channelUrl = channel.channelUrl,
            title = name,
            uploaderName = uploaderName?.takeIf { it.isNotBlank() } ?: channel.name,
            thumbnailUrl = thumbnails.preferredUrl(Image.ResolutionLevel.MEDIUM),
            durationSeconds = duration.takeIf { it > 0 && !live },
            viewCount = viewCount.takeIf { it >= 0 },
            uploadedAt = uploadDate?.instant,
            isLive = live,
        )
    }

    private companion object {
        const val TAG = "SubscriptionRepository"
        const val PARALLEL_CHANNELS = 4
    }
}
