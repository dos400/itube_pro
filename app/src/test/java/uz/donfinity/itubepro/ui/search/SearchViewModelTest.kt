package uz.donfinity.itubepro.ui.search

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.PageToken
import uz.donfinity.itubepro.domain.model.SearchPage
import uz.donfinity.itubepro.domain.repository.SearchRepository
import uz.donfinity.itubepro.domain.usecase.GetSearchSuggestionsUseCase
import uz.donfinity.itubepro.domain.usecase.SearchVideosUseCase
import uz.donfinity.itubepro.testing.MainDispatcherRule
import uz.donfinity.itubepro.testing.video

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    /** Two pages; page 2 repeats a video from page 1 (YouTube does this). */
    private class FakeSearchRepository : SearchRepository {
        val suggestionQueries = mutableListOf<String>()
        var failNextMore = false

        override suspend fun suggestions(query: String): List<String> {
            suggestionQueries += query
            return listOf("$query music")
        }

        override suspend fun search(query: String) =
            SearchPage(listOf(video("a"), video("b")), PageToken("2"), suggestion = null, isCorrectedSearch = false)

        override suspend fun searchMore(query: String, page: PageToken): SearchPage {
            if (failNextMore) {
                failNextMore = false
                throw AppException.Network()
            }
            return SearchPage(listOf(video("b"), video("c")), nextPage = null, suggestion = null, isCorrectedSearch = false)
        }
    }

    private val repository = FakeSearchRepository()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        SearchViewModel(GetSearchSuggestionsUseCase(repository), SearchVideosUseCase(repository), savedState)

    @Test
    fun `search shows first page`() = runTest {
        val vm = viewModel()

        vm.onSearch("  cats ")
        advanceUntilIdle()

        val content = vm.results.value as SearchResultsState.Content
        assertEquals("cats", content.query)
        assertEquals(2, content.items.size)
        assertEquals("query is trimmed and kept", "cats", vm.query.value)
    }

    @Test
    fun `next page is appended without duplicates`() = runTest {
        val vm = viewModel()
        vm.onSearch("cats")
        advanceUntilIdle()

        vm.onLoadMore()
        advanceUntilIdle()

        val content = vm.results.value as SearchResultsState.Content
        assertEquals(listOf("a", "b", "c"), content.items.map { it.title.removePrefix("Video ") })
        assertEquals(null, content.nextPage)
    }

    @Test
    fun `failed next page shows inline error and retry recovers`() = runTest {
        val vm = viewModel()
        vm.onSearch("cats")
        advanceUntilIdle()
        repository.failNextMore = true

        vm.onLoadMore()
        advanceUntilIdle()
        val failed = vm.results.value as SearchResultsState.Content
        assertEquals(AppendState.Error(R.string.error_network), failed.append)

        vm.onLoadMore() // scrolling again must not retry on its own
        advanceUntilIdle()
        assertTrue((vm.results.value as SearchResultsState.Content).append is AppendState.Error)

        vm.onRetryAppend()
        advanceUntilIdle()
        assertEquals(3, (vm.results.value as SearchResultsState.Content).items.size)
    }

    @Test
    fun `suggestions are debounced`() = runTest {
        val vm = viewModel()
        // stateIn(WhileSubscribed) only runs while someone collects.
        backgroundScope.launch { vm.suggestions.collect {} }
        runCurrent()

        vm.onQueryChange("l")
        advanceTimeBy(100)
        vm.onQueryChange("lo")
        advanceTimeBy(100)
        vm.onQueryChange("lofi")
        advanceTimeBy(300)
        runCurrent()

        assertEquals(listOf("lofi"), repository.suggestionQueries)
        assertEquals(listOf("lofi music"), vm.suggestions.value)
    }

    @Test
    fun `restores the last search after process death`() = runTest {
        // What SavedStateHandle contains after Android recreated the process.
        val vm = viewModel(SavedStateHandle(mapOf("query" to "dogs", "submitted_query" to "dogs")))
        advanceUntilIdle()

        assertEquals("dogs", (vm.results.value as SearchResultsState.Content).query)
    }
}
