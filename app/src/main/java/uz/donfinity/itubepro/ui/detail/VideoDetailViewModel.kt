package uz.donfinity.itubepro.ui.detail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.core.player.PlayerConnection
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.VideoDetails
import uz.donfinity.itubepro.domain.repository.SettingsRepository
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
        DetailPlayerState(player = controller, isCurrent = nowPlaying.mediaId == url, audioOnly = audioOnly)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailPlayerState())

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
