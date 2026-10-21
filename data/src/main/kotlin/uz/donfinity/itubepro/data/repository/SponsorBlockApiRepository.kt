package uz.donfinity.itubepro.data.repository

import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import uz.donfinity.itubepro.data.di.IoDispatcher
import uz.donfinity.itubepro.domain.model.SegmentCategory
import uz.donfinity.itubepro.domain.model.SkipSegment
import uz.donfinity.itubepro.domain.repository.SponsorBlockRepository

/**
 * Minimal SponsorBlock client (https://wiki.sponsor.ajay.app/w/API_Docs). Tubular gets
 * segments from its forked extractor; we use the original NewPipeExtractor, so we call
 * the API ourselves.
 *
 * Privacy: we use the hash-prefix endpoint. Only the first 4 hex characters of
 * SHA-256(videoId) are sent, the server answers with every video matching that prefix
 * (~100), and we keep ours. So the server can't tell exactly which video is being watched.
 */
@Singleton
class SponsorBlockApiRepository @Inject constructor(
    client: OkHttpClient,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SponsorBlockRepository {

    // Same connection pool as the rest of the app, but a short overall timeout. SponsorBlock
    // is optional: if it's slow we'd rather play without skipping than wait.
    private val http = client.newBuilder().callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Small in-memory cache: the player and the UI (seek bar markers) ask for the same video. */
    private val cache = object : LinkedHashMap<String, List<SkipSegment>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SkipSegment>>) = size > CACHE_SIZE
    }

    override suspend fun segments(videoUrl: String, categories: Set<SegmentCategory>): List<SkipSegment> {
        if (categories.isEmpty()) return emptyList()
        val key = videoUrl + "|" + categories.map { it.apiName }.sorted().joinToString(",")
        synchronized(cache) { cache[key] }?.let { return it }

        return extract(ioDispatcher) {
            val videoId = ServiceList.YouTube.streamLHFactory.getId(videoUrl)
            val url = HttpUrl.Builder()
                .scheme("https")
                .host(API_HOST)
                .addPathSegments("api/skipSegments")
                .addPathSegment(sha256Hex(videoId).take(HASH_PREFIX_LENGTH))
                // The API expects JSON arrays as query values, e.g. ["sponsor","intro"].
                .addQueryParameter("categories", categories.joinToString(",", "[", "]") { "\"${it.apiName}\"" })
                .addQueryParameter("actionTypes", "[\"skip\"]")
                .build()
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()

            http.newCall(request).execute().use { response ->
                // 404 means "no video with this prefix has segments".
                if (response.code == 404) return@extract emptyList()
                if (!response.isSuccessful) throw IOException("SponsorBlock HTTP ${response.code}")
                json.decodeFromString<List<VideoSegmentsDto>>(response.body.string())
                    .firstOrNull { it.videoID == videoId }
                    ?.segments.orEmpty()
                    .mapNotNull { it.toDomain() }
            }
        }.also { result -> synchronized(cache) { cache[key] = result } }
    }

    @Serializable
    private data class VideoSegmentsDto(val videoID: String, val segments: List<SegmentDto> = emptyList())

    @Serializable
    private data class SegmentDto(val category: String, val actionType: String, val segment: List<Double>)

    private fun SegmentDto.toDomain(): SkipSegment? {
        val type = SegmentCategory.fromApiName(category) ?: return null
        if (actionType != "skip" || segment.size != 2) return null
        return SkipSegment(startMs = (segment[0] * 1000).toLong(), endMs = (segment[1] * 1000).toLong(), category = type)
    }

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val API_HOST = "sponsor.ajay.app"
        const val HASH_PREFIX_LENGTH = 4
        const val CALL_TIMEOUT_SECONDS = 8L
        const val CACHE_SIZE = 20
        const val USER_AGENT = "itubepro/1.0 (personal Android client)"
    }
}
