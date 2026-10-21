package uz.donfinity.itubepro.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.PageToken
import uz.donfinity.itubepro.domain.model.SearchPage
import uz.donfinity.itubepro.domain.repository.SearchRepository

class UseCasesTest {

    /** Records calls instead of hitting the network. */
    private class FakeSearchRepository : SearchRepository {
        val calls = mutableListOf<String>()
        override suspend fun suggestions(query: String): List<String> {
            calls += "suggestions:$query"
            return listOf("$query 1")
        }
        override suspend fun search(query: String): SearchPage {
            calls += "search:$query"
            return SearchPage(emptyList(), PageToken("p2"), null, false)
        }
        override suspend fun searchMore(query: String, page: PageToken): SearchPage {
            calls += "more:$query:${page.value}"
            return SearchPage(emptyList(), null, null, false)
        }
    }

    @Test
    fun `blank suggestion query does not hit the repository`() = runTest {
        val repo = FakeSearchRepository()

        assertTrue(GetSearchSuggestionsUseCase(repo)("   ").isEmpty())
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `suggestion query is trimmed`() = runTest {
        val repo = FakeSearchRepository()

        GetSearchSuggestionsUseCase(repo)("  lofi ")

        assertEquals(listOf("suggestions:lofi"), repo.calls)
    }

    @Test
    fun `search routes first page and next pages`() = runTest {
        val repo = FakeSearchRepository()
        val search = SearchVideosUseCase(repo)

        val first = search("cats")
        search("cats", first.nextPage)

        assertEquals(listOf("search:cats", "more:cats:p2"), repo.calls)
    }

    @Test
    fun `empty video URL fails fast with InvalidUrl`() = runTest {
        val useCase = GetVideoStreamsUseCase(object : uz.donfinity.itubepro.domain.repository.VideoRepository {
            override suspend fun getVideo(url: String) = error("must not be called")
            override suspend fun getDetails(url: String, forceRefresh: Boolean) = error("must not be called")
        })

        try {
            useCase(" ")
            fail("expected InvalidUrl")
        } catch (_: AppException.InvalidUrl) {
        }
    }
}
