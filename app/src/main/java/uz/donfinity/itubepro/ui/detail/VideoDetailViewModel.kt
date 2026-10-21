package uz.donfinity.itubepro.ui.detail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.core.player.PlayerConnection
import uz.donfinity.itubepro.core.player.VideoQuality
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.Playlist
import uz.donfinity.itubepro.domain.model.Subscription
import uz.donfinity.itubepro.domain.model.VideoDetails
import uz.donfinity.itubepro.domain.model.toSummary
import uz.donfinity.itubepro.domain.repository.FavoritesRepository
import uz.donfinity.itubepro.domain.repository.PlaylistRepository
import uz.donfinity.itubepro.domain.repository.SettingsRepository
import uz.donfinity.itubepro.domain.repository.SubscriptionRepository
import uz.donfinity.itubepro.domain.usecase.GetVideoDetailsUseCase
import uz.donfinity.itubepro.navigation.VideoDetailRoute
import uz.donfinity.itubepro.ui.common.messageRes

sealed interface VideoDetailUiState {
    data object Loading : VideoDetailUiState
    data class Error(@param:StringRes val messageRes: Int) : VideoDetailUiState
    data class Content(val details: VideoDetails) : VideoDetailUiState
}

/** Player-related part of the screen. [player] is the MediaController, used to bind a PlayerView. */
data class DetailPlayerState(
    val player: Player? = null,
    /** True when this screen's video is the one loaded in the player. */
    val isCurrent: Boolean = false,
    val audioOnly: Boolean = false,
    /** A video track is playing (false in audio-only mode or before tracks are known). */
    val hasVideo: Boolean = false,
    val qualities: List<VideoQuality> = emptyList(),
    val selectedQuality: VideoQuality? = null,
    val playingHeight: Int = 0,
)

/** Library part of the screen: favorite heart, playlist checkboxes, subscribe button. */
data class DetailLibraryState(
    val isFavorite: Boolean = false,
    val isSubscribed: Boolean = false,
    val playlists: List<Playlist> = emptyList(),
    /** IDs of playlists that already contain this video. */
    val inPlaylists: Set<Long> = emptySet(),
)

/**
 * The route arguments (the video URL) arrive through SavedStateHandle. Navigation stores
 * them in the back stack entry, so they also survive process death.
 */
@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getVideoDetails: GetVideoDetailsUseCase,
    private val playerConnection: PlayerConnection,
    private val settings: SettingsRepository,
    private val favorites: FavoritesRepository,
    private val playlists: PlaylistRepository,
    private val subscriptions: SubscriptionRepository,
) : ViewModel() {

    /** Also used as the player's mediaId, so "is this video playing?" is a plain comparison. */
    private val url: String = savedStateHandle.toRoute<VideoDetailRoute>().url

    private val _state = MutableStateFlow<VideoDetailUiState>(VideoDetailUiState.Loading)
    val state: StateFlow<VideoDetailUiState> = _state.asStateFlow()

    val playerState: StateFlow<DetailPlayerState> = combine(
        playerConnection.controller,
        playerConnection.state,
        settings.audioOnly,
    ) { controller, nowPlaying, audioOnly ->
        val isCurrent = nowPlaying.mediaId == url
        DetailPlayerState(
            player = controller,
            isCurrent = isCurrent,
            audioOnly = audioOnly,
            hasVideo = isCurrent && nowPlaying.hasVideo,
            qualities = if (isCurrent) nowPlaying.qualities else emptyList(),
            selectedQuality = nowPlaying.selectedQuality.takeIf { isCurrent },
            playingHeight = if (isCurrent) nowPlaying.videoHeight else 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailPlayerState())

    /** The loaded details, or null while loading / on error. */
    private val details = _state.map { (it as? VideoDetailUiState.Content)?.details }

    @OptIn(ExperimentalCoroutinesApi::class)
    val libraryState: StateFlow<DetailLibraryState> = combine(
        favorites.isFavorite(url),
        playlists.observePlaylists(),
        playlists.playlistsContaining(url),
        // The channel URL is only known after loading, so switch to its flow once we have it.
        details.flatMapLatest { d -> d?.uploaderUrl?.let(subscriptions::isSubscribed) ?: flowOf(false) },
    ) { favorite, allPlaylists, containing, subscribed ->
        DetailLibraryState(favorite, subscribed, allPlaylists, containing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailLibraryState())

    private var loadJob: Job? = null

    init {
        load(forceRefresh = false)
    }

    fun onRetry() = load(forceRefresh = true)

    fun onPlay() {
        val details = (_state.value as? VideoDetailUiState.Content)?.details ?: return
        playerConnection.playVideo(
            videoUrl = url,
            title = details.title,
            artist = details.uploaderName,
            artworkUrl = details.thumbnailUrl,
        )
    }

    fun onSelectQuality(quality: VideoQuality?) = playerConnection.selectQuality(quality)

    fun onToggleFavorite() {
        val video = loadedDetails()?.toSummary() ?: return
        viewModelScope.launch { favorites.setFavorite(video, !libraryState.value.isFavorite) }
    }

    fun onTogglePlaylist(playlistId: Long) {
        val video = loadedDetails()?.toSummary() ?: return
        viewModelScope.launch {
            if (playlistId in libraryState.value.inPlaylists) {
                playlists.removeVideo(playlistId, video.url)
            } else {
                playlists.addVideo(playlistId, video)
            }
        }
    }

    fun onCreatePlaylistWithVideo(name: String) {
        val video = loadedDetails()?.toSummary() ?: return
        viewModelScope.launch { playlists.addVideo(playlists.create(name), video) }
    }

    fun onToggleSubscribe() {
        val d = loadedDetails() ?: return
        val channelUrl = d.uploaderUrl ?: return
        viewModelScope.launch {
            if (libraryState.value.isSubscribed) {
                subscriptions.unsubscribe(channelUrl)
            } else {
                subscriptions.subscribe(Subscription(channelUrl, d.uploaderName, d.uploaderAvatarUrl))
            }
        }
    }

    private fun loadedDetails(): VideoDetails? = (_state.value as? VideoDetailUiState.Content)?.details

    fun onToggleAudioOnly() {
        viewModelScope.launch { settings.setAudioOnly(!settings.audioOnly.first()) }
    }

    private fun load(forceRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = VideoDetailUiState.Loading
            _state.value = try {
                VideoDetailUiState.Content(getVideoDetails(url, forceRefresh))
            } catch (e: AppException) {
                VideoDetailUiState.Error(e.messageRes())
            }
        }
    }
}
