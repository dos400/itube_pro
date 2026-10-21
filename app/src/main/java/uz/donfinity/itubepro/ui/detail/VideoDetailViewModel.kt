package uz.donfinity.itubepro.ui.detail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.VideoDetails
import uz.donfinity.itubepro.domain.usecase.GetVideoDetailsUseCase
import uz.donfinity.itubepro.navigation.VideoDetailRoute
import uz.donfinity.itubepro.ui.common.messageRes

sealed interface VideoDetailUiState {
    data object Loading : VideoDetailUiState
    data class Error(@param:StringRes val messageRes: Int) : VideoDetailUiState
    data class Content(val details: VideoDetails) : VideoDetailUiState
}

/**
 * The route arguments (the video URL) arrive through SavedStateHandle. Navigation stores
 * them in the back stack entry, so they also survive process death.
 */
@HiltViewModel
class VideoDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getVideoDetails: GetVideoDetailsUseCase,
) : ViewModel() {

    private val url: String = savedStateHandle.toRoute<VideoDetailRoute>().url

    private val _state = MutableStateFlow<VideoDetailUiState>(VideoDetailUiState.Loading)
    val state: StateFlow<VideoDetailUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load(forceRefresh = false)
    }

    fun onRetry() = load(forceRefresh = true)

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
