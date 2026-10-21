package uz.donfinity.itubepro.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.FeedItem
import uz.donfinity.itubepro.domain.repository.SubscriptionRepository
import uz.donfinity.itubepro.ui.common.messageRes

data class FeedUiState(
    val items: List<FeedItem>,
    val hasSubscriptions: Boolean,
)

/** One-off messages for the snackbar. */
sealed interface FeedMessage {
    data class Error(val messageRes: Int) : FeedMessage
    data class SomeChannelsFailed(val count: Int) : FeedMessage
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val subscriptions: SubscriptionRepository,
) : ViewModel() {

    /** Straight from the database: shows the last downloaded feed instantly, even offline. */
    val state: StateFlow<FeedUiState?> = combine(
        subscriptions.observeFeed(),
        subscriptions.observeSubscriptions(),
    ) { items, subs -> FeedUiState(items, subs.isNotEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    // A Channel (not a StateFlow) for events: each message is delivered once, even if it
    // arrives while the screen is briefly gone (e.g. during rotation).
    private val _messages = Channel<FeedMessage>(Channel.BUFFERED)
    val messages: Flow<FeedMessage> = _messages.receiveAsFlow()

    init {
        // Opening the tab refreshes automatically when the feed is old (or was never loaded).
        viewModelScope.launch {
            val last = subscriptions.observeLastFeedRefresh().first()
            val stale = last == null || Duration.between(last, Instant.now()) > AUTO_REFRESH_AFTER
            if (stale && subscriptions.observeSubscriptions().first().isNotEmpty()) onRefresh()
        }
    }

    fun onRefresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val result = subscriptions.refreshFeed()
                if (result.failedChannels > 0) _messages.send(FeedMessage.SomeChannelsFailed(result.failedChannels))
            } catch (e: AppException) {
                _messages.send(FeedMessage.Error(e.messageRes()))
            } finally {
                _refreshing.value = false
            }
        }
    }

    private companion object {
        val AUTO_REFRESH_AFTER: Duration = Duration.ofMinutes(30)
    }
}
