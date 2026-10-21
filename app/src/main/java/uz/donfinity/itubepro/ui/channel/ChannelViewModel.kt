package uz.donfinity.itubepro.ui.channel

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.ChannelDetails
import uz.donfinity.itubepro.domain.model.PageToken
import uz.donfinity.itubepro.domain.model.Subscription
import uz.donfinity.itubepro.domain.model.VideoSummary
import uz.donfinity.itubepro.domain.repository.ChannelRepository
import uz.donfinity.itubepro.domain.repository.SubscriptionRepository
import uz.donfinity.itubepro.navigation.ChannelRoute
import uz.donfinity.itubepro.ui.common.AppendState
import uz.donfinity.itubepro.ui.common.messageRes

sealed interface ChannelUiState {
    data object Loading : ChannelUiState
    data class Error(@param:StringRes val messageRes: Int) : ChannelUiState
    data class Content(
        val channel: ChannelDetails,
        val videos: List<VideoSummary>,
        val nextPage: PageToken?,
        val append: AppendState = AppendState.Idle,
    ) : ChannelUiState
}

/** Same paging rules as SearchViewModel: guards in the ViewModel, de-duplicated by URL. */
@HiltViewModel
class ChannelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val channels: ChannelRepository,
    private val subscriptions: SubscriptionRepository,
) : ViewModel() {

    private val url: String = savedStateHandle.toRoute<ChannelRoute>().url

    private val _state = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    val isSubscribed: StateFlow<Boolean> =
        subscriptions.isSubscribed(url).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var loadJob: Job? = null
    private var appendJob: Job? = null

    init {
        load()
    }

    fun onRetry() = load()

    fun onLoadMore() {
        val current = _state.value as? ChannelUiState.Content ?: return
        if (current.nextPage == null || current.append != AppendState.Idle) return
        loadNextPage(current)
    }

    fun onRetryAppend() {
        val current = _state.value as? ChannelUiState.Content ?: return
        if (current.nextPage == null || current.append is AppendState.Loading) return
        loadNextPage(current)
    }

    fun onToggleSubscribe() {
        val channel = (_state.value as? ChannelUiState.Content)?.channel ?: return
        viewModelScope.launch {
            // Subscribe under the URL we were opened with: that's what isSubscribed checks and
            // what the video detail screen uses, so all screens agree.
            if (isSubscribed.value) {
                subscriptions.unsubscribe(url)
            } else {
                subscriptions.subscribe(Subscription(url, channel.name, channel.avatarUrl))
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        appendJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = ChannelUiState.Loading
            _state.value = try {
                val result = channels.getChannel(url)
                ChannelUiState.Content(
                    channel = result.channel,
                    videos = result.firstPage.items.distinctBy { it.url },
                    nextPage = result.firstPage.nextPage,
                )
            } catch (e: AppException) {
                ChannelUiState.Error(e.messageRes())
            }
        }
    }

    private fun loadNextPage(current: ChannelUiState.Content) {
        val token = current.nextPage ?: return
        _state.value = current.copy(append = AppendState.Loading)
        appendJob = viewModelScope.launch {
            try {
                val page = channels.moreVideos(token)
                _state.update { state ->
                    if (state !is ChannelUiState.Content) {
                        state
                    } else {
                        state.copy(
                            videos = (state.videos + page.items).distinctBy { it.url },
                            nextPage = page.nextPage,
                            append = AppendState.Idle,
                        )
                    }
                }
            } catch (e: AppException) {
                _state.update { state ->
                    if (state is ChannelUiState.Content) state.copy(append = AppendState.Error(e.messageRes())) else state
                }
            }
        }
    }
}
