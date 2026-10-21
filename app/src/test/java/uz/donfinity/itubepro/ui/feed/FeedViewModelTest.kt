package uz.donfinity.itubepro.ui.feed

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.FeedItem
import uz.donfinity.itubepro.domain.model.FeedRefreshResult
import uz.donfinity.itubepro.domain.model.Subscription
import uz.donfinity.itubepro.domain.repository.SubscriptionRepository
import uz.donfinity.itubepro.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeSubscriptionRepository(
        lastRefresh: Instant?,
        subscriptions: List<Subscription> = listOf(Subscription("https://youtube.com/channel/UC1", "One", null)),
    ) : SubscriptionRepository {
        val subs = MutableStateFlow(subscriptions)
        val feed = MutableStateFlow<List<FeedItem>>(emptyList())
        val last = MutableStateFlow(lastRefresh)
        var refreshCalls = 0
        var result: () -> FeedRefreshResult = { FeedRefreshResult(1, 0) }

        override fun observeSubscriptions(): Flow<List<Subscription>> = subs
        override fun isSubscribed(channelUrl: String) = subs.map { list -> list.any { it.channelUrl == channelUrl } }
        override suspend fun subscribe(subscription: Subscription) { subs.value += subscription }
        override suspend fun unsubscribe(channelUrl: String) { subs.value = subs.value.filterNot { it.channelUrl == channelUrl } }
        override fun observeFeed(): Flow<List<FeedItem>> = feed
        override fun observeLastFeedRefresh(): Flow<Instant?> = last
        override suspend fun refreshFeed(): FeedRefreshResult {
            refreshCalls++
            return result()
        }
    }

    @Test
    fun `stale feed refreshes on open and reports failed channels`() = runTest {
        val repo = FakeSubscriptionRepository(lastRefresh = null).apply { result = { FeedRefreshResult(2, 1) } }

        val vm = FeedViewModel(repo)
        advanceUntilIdle()

        assertEquals(1, repo.refreshCalls)
        assertEquals(FeedMessage.SomeChannelsFailed(1), vm.messages.first())
        assertFalse(vm.refreshing.value)
    }

    @Test
    fun `fresh feed does not refresh by itself`() = runTest {
        val repo = FakeSubscriptionRepository(lastRefresh = Instant.now().minus(Duration.ofMinutes(5)))

        FeedViewModel(repo)
        advanceUntilIdle()

        assertEquals(0, repo.refreshCalls)
    }

    @Test
    fun `no subscriptions means no refresh`() = runTest {
        val repo = FakeSubscriptionRepository(lastRefresh = null, subscriptions = emptyList())

        FeedViewModel(repo)
        advanceUntilIdle()

        assertEquals(0, repo.refreshCalls)
    }

    @Test
    fun `refresh error becomes a message`() = runTest {
        val repo = FakeSubscriptionRepository(lastRefresh = Instant.now()).apply { result = { throw AppException.RateLimited() } }
        val vm = FeedViewModel(repo)

        vm.onRefresh()
        advanceUntilIdle()

        assertEquals(FeedMessage.Error(R.string.error_rate_limited), vm.messages.first())
    }
}
