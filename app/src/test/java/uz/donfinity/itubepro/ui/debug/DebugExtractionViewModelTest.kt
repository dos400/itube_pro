package uz.donfinity.itubepro.ui.debug

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.ExtractedVideo
import uz.donfinity.itubepro.domain.repository.VideoRepository
import uz.donfinity.itubepro.domain.usecase.GetVideoStreamsUseCase
import uz.donfinity.itubepro.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class DebugExtractionViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeVideoRepository(private val fail: AppException? = null) : VideoRepository {
        val requested = mutableListOf<String>()
        override suspend fun getVideo(url: String): ExtractedVideo {
            requested += url
            fail?.let { throw it }
            return ExtractedVideo("id", url, "Title", "Uploader", 10, null, null, emptyList())
        }
        override suspend fun getDetails(url: String, forceRefresh: Boolean) = error("not used")
    }

    @Test
    fun `extract shows the video`() = runTest {
        val repo = FakeVideoRepository()
        val vm = DebugExtractionViewModel(GetVideoStreamsUseCase(repo), SavedStateHandle())

        vm.onUrlChange("https://youtu.be/x")
        vm.onExtractClick()
        advanceUntilIdle()

        assertEquals("Title", (vm.state.value as DebugUiState.Success).video.title)
    }

    @Test
    fun `failure maps to a string resource`() = runTest {
        val vm = DebugExtractionViewModel(GetVideoStreamsUseCase(FakeVideoRepository(AppException.Network())), SavedStateHandle())

        vm.onExtractClick()
        advanceUntilIdle()

        assertEquals(DebugUiState.Error(R.string.error_network), vm.state.value)
    }

    @Test
    fun `process death re-runs the last submitted extraction`() = runTest {
        val repo = FakeVideoRepository()

        val vm = DebugExtractionViewModel(GetVideoStreamsUseCase(repo), SavedStateHandle(mapOf("submitted_url" to "https://youtu.be/restored")))
        advanceUntilIdle()

        assertEquals(listOf("https://youtu.be/restored"), repo.requested)
        assertTrue(vm.state.value is DebugUiState.Success)
    }
}
