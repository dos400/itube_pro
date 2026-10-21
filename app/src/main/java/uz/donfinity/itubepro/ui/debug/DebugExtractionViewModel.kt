package uz.donfinity.itubepro.ui.debug

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.ExtractedVideo
import uz.donfinity.itubepro.domain.usecase.GetVideoStreamsUseCase
import uz.donfinity.itubepro.ui.common.messageRes

sealed interface DebugUiState {
    data object Idle : DebugUiState
    data object Loading : DebugUiState
    data class Success(val video: ExtractedVideo) : DebugUiState
    data class Error(@param:StringRes val messageRes: Int) : DebugUiState
}

/**
 * State that survives what:
 * - Rotation: the ViewModel itself survives, so [state] is kept as-is.
 * - Process death: the ViewModel is recreated. Only [SavedStateHandle] survives, so we
 *   store the typed URL and the last submitted URL there and extract again on restore.
 *   The result itself isn't saved: stream URLs expire, and a Bundle has a size limit.
 */
@HiltViewModel
class DebugExtractionViewModel @Inject constructor(
    private val getVideoStreams: GetVideoStreamsUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val url: StateFlow<String> = savedStateHandle.getStateFlow(KEY_URL, SAMPLE_URL)

    private val _state = MutableStateFlow<DebugUiState>(DebugUiState.Idle)
    val state: StateFlow<DebugUiState> = _state.asStateFlow()

    private var extractJob: Job? = null

    init {
        savedStateHandle.get<String>(KEY_SUBMITTED_URL)?.let(::extract)
    }

    fun onUrlChange(value: String) {
        savedStateHandle[KEY_URL] = value
    }

    fun onExtractClick() = extract(url.value)

    fun onRetryClick() {
        savedStateHandle.get<String>(KEY_SUBMITTED_URL)?.let(::extract)
    }

    private fun extract(target: String) {
        savedStateHandle[KEY_SUBMITTED_URL] = target
        // Cancel a request that's still running so a slow old result can't overwrite a newer one.
        extractJob?.cancel()
        extractJob = viewModelScope.launch {
            _state.value = DebugUiState.Loading
            _state.value = try {
                DebugUiState.Success(getVideoStreams(target))
            } catch (e: AppException) {
                DebugUiState.Error(e.messageRes())
            }
        }
    }

    private companion object {
        const val KEY_URL = "url"
        const val KEY_SUBMITTED_URL = "submitted_url"

        // "Me at the zoo": the first YouTube video. Short, public, and has been up since 2005.
        const val SAMPLE_URL = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
    }
}
