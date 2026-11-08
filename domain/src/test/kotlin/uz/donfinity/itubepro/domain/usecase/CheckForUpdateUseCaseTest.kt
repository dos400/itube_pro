package uz.donfinity.itubepro.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uz.donfinity.itubepro.domain.model.AppInfo
import uz.donfinity.itubepro.domain.model.AppRelease
import uz.donfinity.itubepro.domain.repository.AppUpdateRepository

class CheckForUpdateUseCaseTest {

    private class FakeRepository(private val latest: AppRelease?) : AppUpdateRepository {
        val requested = mutableListOf<String>()
        override suspend fun latestRelease(repo: String): AppRelease? {
            requested += repo
            return latest
        }
    }

    private fun release(code: Int) = AppRelease(code, "1.$code", "https://example.com/$code.apk", 1L)
    private fun app(code: Int, repo: String = "me/itubepro") = AppInfo(code, "1.$code", repo, lastUpdatedAtMillis = 0L)

    @Test
    fun `newer release is offered`() = runTest {
        val repo = FakeRepository(release(42))
        assertEquals(release(42), CheckForUpdateUseCase(repo, app(41))())
        assertEquals(listOf("me/itubepro"), repo.requested)
    }

    @Test
    fun `same or older release is not offered`() = runTest {
        assertNull(CheckForUpdateUseCase(FakeRepository(release(42)), app(42))())
        assertNull(CheckForUpdateUseCase(FakeRepository(release(40)), app(42))())
    }

    @Test
    fun `no release means no update`() = runTest {
        assertNull(CheckForUpdateUseCase(FakeRepository(null), app(1))())
    }

    @Test
    fun `no update repo means no network request`() = runTest {
        val repo = FakeRepository(release(99))
        assertNull(CheckForUpdateUseCase(repo, app(1, repo = ""))())
        assertEquals(emptyList<String>(), repo.requested)
    }
}
