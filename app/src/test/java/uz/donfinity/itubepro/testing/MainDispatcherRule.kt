package uz.donfinity.itubepro.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import uz.donfinity.itubepro.domain.model.VideoSummary

/**
 * viewModelScope runs on Dispatchers.Main, which doesn't exist in plain JVM tests. This rule
 * swaps in a test dispatcher. `runTest` then shares its virtual clock, so `delay`/`debounce`
 * finish instantly when we call advanceTimeBy / advanceUntilIdle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

fun video(id: String) = VideoSummary(
    url = "https://www.youtube.com/watch?v=$id",
    title = "Video $id",
    uploaderName = "Channel",
    thumbnailUrl = null,
    durationSeconds = 60,
    viewCount = 1,
    uploadedText = null,
    isLive = false,
)
