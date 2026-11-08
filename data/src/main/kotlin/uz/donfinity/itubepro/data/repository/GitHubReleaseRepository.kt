package uz.donfinity.itubepro.data.repository

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import uz.donfinity.itubepro.data.di.IoDispatcher
import uz.donfinity.itubepro.domain.model.AppRelease
import uz.donfinity.itubepro.domain.repository.AppUpdateRepository

/**
 * Reads the latest release from the GitHub REST API (no token: the repo is public, and
 * the unauthenticated limit of 60 requests/hour per IP is plenty for one check per app start).
 * CI publishes each build as release `v<versionCode>` with the APK attached (release.yml).
 */
@Singleton
class GitHubReleaseRepository @Inject constructor(
    client: OkHttpClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AppUpdateRepository {

    private val http = client.newBuilder().callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

    override suspend fun latestRelease(repo: String): AppRelease? = extract(ioDispatcher) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            // GitHub rejects API requests without a User-Agent.
            .header("User-Agent", USER_AGENT)
            .build()
        http.newCall(request).execute().use { response ->
            // 404 = the repo has no published release yet.
            if (response.code == 404) return@extract null
            if (!response.isSuccessful) throw IOException("GitHub HTTP ${response.code}")
            parseLatestRelease(response.body.string())
        }
    }

    private companion object {
        const val CALL_TIMEOUT_SECONDS = 15L
        const val USER_AGENT = "itubepro-updater"
    }
}

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class ReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val assets: List<AssetDto> = emptyList(),
)

@Serializable
private data class AssetDto(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = -1,
)

/** Null if the tag isn't `v<number>` or the release has no APK attached. */
internal fun parseLatestRelease(body: String): AppRelease? {
    val dto = json.decodeFromString<ReleaseDto>(body)
    val code = versionCodeFromTag(dto.tagName) ?: return null
    val apk = dto.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
    return AppRelease(
        versionCode = code,
        versionName = dto.name?.takeIf { it.isNotBlank() } ?: dto.tagName,
        apkUrl = apk.downloadUrl,
        apkSizeBytes = apk.size,
    )
}

internal fun versionCodeFromTag(tag: String): Int? =
    Regex("""^v(\d+)$""").find(tag)?.groupValues?.get(1)?.toIntOrNull()
