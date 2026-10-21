package uz.donfinity.itubepro.ui.search

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.PageToken
import uz.donfinity.itubepro.domain.model.VideoSummary
import uz.donfinity.itubepro.domain.usecase.GetSearchSuggestionsUseCase
import uz.donfinity.itubepro.domain.usecase.SearchVideosUseCase
import uz.donfinity.itubepro.ui.common.AppendState
import uz.donfinity.itubepro.ui.common.messageRes

sealed interface SearchResultsState {
    /** Nothing searched yet. */
    data object Idle : SearchResultsState
    data object Loading : SearchResultsState
    data class Error(@param:StringRes val messageRes: Int) : SearchResultsState
    data class Empty(val query: String) : SearchResultsState
    data class Content(
        val query: String,
        val items: List<VideoSummary>,
        val nextPage: PageToken?,
        val suggestion: String?,
        val isCorrectedSearch: Boolean,
        val append: AppendState = AppendState.Idle,
    ) : SearchResultsState
}

/**
 * Three separate streams instead of one combined UiState:
 * - [query] comes straight from SavedStateHandle and updates synchronously. A TextField
 *   needs that: if its value goes through an async `combine().stateIn()`, fast typing can
 *   drop characters or make the cursor jump.
 * - [suggestions] is derived from [query] (debounced).
 * - [results] changes only when the user submits or scrolls.
 *
 * Process death: the query and the submitted query live in SavedStateHandle. On restore
 * we re-run the search and show the first page again. Old page tokens aren't saved.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val getSuggestions: GetSearchSuggestionsUseCase,
    private val searchVideos: SearchVideosUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    val suggestions: StateFlow<List<String>> = query
        // Wait until typing pauses. ref/NewPipe uses 120 ms; a little longer means fewer
        // requests on a slow keyboard.
        .debounce(SUGGESTION_DEBOUNCE_MS)
        .distinctUntilChanged()
        // mapLatest cancels the previous request when a new query arrives, so an old
        // slow response can't overwrite a newer one.
        .mapLatest { q ->
            try {
                getSuggestions(q)
            } catch (_: AppException) {
                emptyList() // Suggestions are nice-to-have; fail silently.
            }
        }
        // WhileSubscribed(5s): keep the flow running through a rotation but stop it
        // when the screen is really gone.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _results = MutableStateFlow<SearchResultsState>(SearchResultsState.Idle)
    val results: StateFlow<SearchResultsState> = _results.asStateFlow()

    /** The query whose results are on screen (null before the first search). */
    val submittedQuery: String? get() = savedStateHandle[KEY_SUBMITTED]

    private var searchJob: Job? = null
    private var appendJob: Job? = null

    init {
        submittedQuery?.let(::runSearch)
    }

    fun onQueryChange(value: String) {
        savedStateHandle[KEY_QUERY] = value
    }

    fun onSearch(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        savedStateHandle[KEY_QUERY] = trimmed
        savedStateHandle[KEY_SUBMITTED] = trimmed
        runSearch(trimmed)
    }

    fun onRetry() {
        submittedQuery?.let(::runSearch)
    }

    /** Called by the list when the user scrolls close to the end. Safe to call often. */
    fun onLoadMore() {
        val current = _results.value as? SearchResultsState.Content ?: return
        // Guards (same as ref/NewPipe BaseListFragment): nothing left, already loading,
        // or the last attempt failed (then the user has to tap Retry).
        if (current.nextPage == null || current.append != AppendState.Idle) return
        loadNextPage(current)
    }

    fun onRetryAppend() {
        val current = _results.value as? SearchResultsState.Content ?: return
        if (current.nextPage == null || current.append is AppendState.Loading) return
        loadNextPage(current)
    }

    private fun runSearch(query: String) {
        searchJob?.cancel()
        appendJob?.cancel()
        searchJob = viewModelScope.launch {
            _results.value = SearchResultsState.Loading
            _results.value = try {
                val page = searchVideos(query)
                if (page.items.isEmpty()) {
                    SearchResultsState.Empty(query)
                } else {
                    SearchResultsState.Content(
                        query = query,
                        items = page.items.distinctBy { it.url },
                        nextPage = page.nextPage,
                        suggestion = page.suggestion,
                        isCorrectedSearch = page.isCorrectedSearch,
                    )
                }
            } catch (e: AppException) {
                SearchResultsState.Error(e.messageRes())
            }
        }
    }

    private fun loadNextPage(current: SearchResultsState.Content) {
        val token = current.nextPage ?: return
        _results.value = current.copy(append = AppendState.Loading)
        appendJob = viewModelScope.launch {
            try {
                val page = searchVideos(current.query, token)
                _results.update { state ->
                    // The user may have started a new search meanwhile; only append to
                    // the same result list.
                    if (state !is SearchResultsState.Content || state.query != current.query) {
                        state
                    } else {
                        state.copy(
                            // YouTube sometimes repeats a video on the next page. Duplicate
                            // LazyColumn keys would crash, so deduplicate by URL.
                            items = (state.items + page.items).distinctBy { it.url },
                            nextPage = page.nextPage,
                            append = AppendState.Idle,
                        )
                    }
                }
            } catch (e: AppException) {
                _results.update { state ->
                    if (state is SearchResultsState.Content) {
                        state.copy(append = AppendState.Error(e.messageRes()))
                    } else {
                        state
                    }
                }
            }
        }
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_SUBMITTED = "submitted_query"
        const val SUGGESTION_DEBOUNCE_MS = 250L
    }
}
