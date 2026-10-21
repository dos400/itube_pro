package uz.donfinity.itubepro.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.domain.model.HistoryItem
import uz.donfinity.itubepro.domain.model.Playlist
import uz.donfinity.itubepro.domain.model.PlaylistWithItems
import uz.donfinity.itubepro.domain.model.Subscription
import uz.donfinity.itubepro.domain.model.VideoSummary
import uz.donfinity.itubepro.domain.repository.FavoritesRepository
import uz.donfinity.itubepro.domain.repository.HistoryRepository
import uz.donfinity.itubepro.domain.repository.PlaylistRepository
import uz.donfinity.itubepro.domain.repository.SubscriptionRepository
import uz.donfinity.itubepro.navigation.PlaylistRoute

/*
 * The library ViewModels are thin. Repositories already expose Flows that update after every
 * write, so a ViewModel only turns them into StateFlows (stateIn) and forwards user actions.
 * No use cases in between: they would only forward calls (same reasoning as SettingsViewModel).
 *
 * The `null` initial value means "not loaded yet", so screens can tell loading apart from empty.
 */

private const val STOP_TIMEOUT_MS = 5_000L

data class LibraryOverview(
    val playlists: List<Playlist>,
    val historyCount: Int,
    val favoritesCount: Int,
    val subscriptionsCount: Int,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    history: HistoryRepository,
    favorites: FavoritesRepository,
    subscriptions: SubscriptionRepository,
    private val playlists: PlaylistRepository,
) : ViewModel() {

    val overview: StateFlow<LibraryOverview?> = combine(
        playlists.observePlaylists(),
        history.observeHistory(),
        favorites.observeFavorites(),
        subscriptions.observeSubscriptions(),
    ) { playlistList, historyList, favoriteList, subscriptionList ->
        LibraryOverview(playlistList, historyList.size, favoriteList.size, subscriptionList.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun onCreatePlaylist(name: String) {
        viewModelScope.launch { playlists.create(name) }
    }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
) : ViewModel() {

    val items: StateFlow<List<HistoryItem>?> =
        history.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun onRemove(url: String) {
        viewModelScope.launch { history.remove(url) }
    }

    fun onClear() {
        viewModelScope.launch { history.clear() }
    }
}

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favorites: FavoritesRepository,
) : ViewModel() {

    val items: StateFlow<List<VideoSummary>?> =
        favorites.observeFavorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun onRemove(video: VideoSummary) {
        viewModelScope.launch { favorites.setFavorite(video, favorite = false) }
    }
}

sealed interface PlaylistUiState {
    data object Loading : PlaylistUiState
    /** The playlist was deleted (here or elsewhere); the screen should close. */
    data object Gone : PlaylistUiState
    data class Content(val playlist: PlaylistWithItems) : PlaylistUiState
}

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlists: PlaylistRepository,
) : ViewModel() {

    private val id: Long = savedStateHandle.toRoute<PlaylistRoute>().id

    val state: StateFlow<PlaylistUiState> = playlists.observePlaylist(id)
        .map { it?.let(PlaylistUiState::Content) ?: PlaylistUiState.Gone }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlaylistUiState.Loading)

    fun onRemove(url: String) {
        viewModelScope.launch { playlists.removeVideo(id, url) }
    }

    fun onRename(name: String) {
        viewModelScope.launch { playlists.rename(id, name) }
    }

    fun onDelete() {
        viewModelScope.launch { playlists.delete(id) }
    }
}

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptions: SubscriptionRepository,
) : ViewModel() {

    val items: StateFlow<List<Subscription>?> =
        subscriptions.observeSubscriptions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun onUnsubscribe(channelUrl: String) {
        viewModelScope.launch { subscriptions.unsubscribe(channelUrl) }
    }
}
