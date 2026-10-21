package uz.donfinity.itubepro.ui.home

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.Recommendations
import uz.donfinity.itubepro.domain.usecase.GetRecommendationsUseCase
import uz.donfinity.itubepro.ui.common.messageRes

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(@param:StringRes val messageRes: Int) : HomeUiState
    data class Content(
        val recommendations: Recommendations,
        /** Pull-to-refresh is running; the old list stays visible meanwhile. */
        val refreshing: Boolean = false,
        /** The last refresh failed; shown once as a snackbar, the old list stays. */
        @param:StringRes val refreshError: Int? = null,
    ) : HomeUiState
}

/**
 * Recommendations are network results, so they aren't saved for process death: the list is
 * simply built again (seeds are random, so it may differ a little).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getRecommendations: GetRecommendationsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun onRetry() = load()

    fun onRefresh() {
        val current = _state.value as? HomeUiState.Content ?: return load()
        if (current.refreshing) return
        _state.value = current.copy(refreshing = true, refreshError = null)
        job = viewModelScope.launch {
            _state.value = try {
                HomeUiState.Content(getRecommendations())
            } catch (e: AppException) {
                current.copy(refreshing = false, refreshError = e.messageRes())
            }
        }
    }

    fun onRefreshErrorShown() {
        _state.update { if (it is HomeUiState.Content) it.copy(refreshError = null) else it }
    }

    private fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = HomeUiState.Loading
            _state.value = try {
                HomeUiState.Content(getRecommendations())
            } catch (e: AppException) {
                HomeUiState.Error(e.messageRes())
            }
        }
    }
}
