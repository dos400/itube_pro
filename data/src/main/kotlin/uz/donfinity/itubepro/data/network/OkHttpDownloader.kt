package uz.donfinity.itubepro.data.network

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * NewPipeExtractor makes no HTTP calls of its own. It hands every request to the
 * [Downloader] we register in `NewPipe.init`, and this class runs them on OkHttp.
 *
 * Idea from ref/NewPipe/app/src/main/java/org/schabi/newpipe/DownloaderImpl.java:
 * fixed desktop User-Agent, extra cookies, extractor headers override defaults,
 * HTTP 429 → [ReCaptchaException].
 *
 * [execute] is blocking. Callers must be on a background thread (the repositories
 * switch to Dispatchers.IO).
 */
@Singleton
class OkHttpDownloader @Inject constructor(
    private val client: OkHttpClient,
) : Downloader() {

    /**
     * Named cookie sets that get added to every request, e.g. a future "restricted mode"
     * `PREF` cookie. Keyed by a name of our choosing so a set can be replaced or removed
     * on its own.
     */
    private val extraCookies = ConcurrentHashMap<String, String>()

    fun setCookie(key: String, cookie: String) {
        extraCookies[key] = cookie
    }

    fun removeCookie(key: String) {
        extraCookies.remove(key)
    }

    override fun execute(request: Request): Response {
        val url = request.url()
        val body = request.dataToSend()?.toRequestBody()

        val builder = okhttp3.Request.Builder()
            .url(url)
            .method(request.httpMethod(), body)
            .header("User-Agent", USER_AGENT)

        val cookieHeader = extraCookies.values
            .flatMap { it.split(';') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("; ")
        if (cookieHeader.isNotEmpty()) builder.header("Cookie", cookieHeader)

        // The extractor's own headers win over our defaults (same rule as NewPipe).
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == HTTP_TOO_MANY_REQUESTS) {
                // YouTube rate-limits by IP and wants a captcha solved. NewPipe opens a
                // WebView for that; we don't use WebView, so the UI just shows an error.
                throw ReCaptchaException("reCAPTCHA challenge requested", url)
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                // The final URL after redirects. The extractor uses it, e.g. for consent pages.
                response.request.url.toString(),
            )
        }
    }

    companion object {
        private const val HTTP_TOO_MANY_REQUESTS = 429

        /**
         * YouTube serves different (and better-supported) responses to desktop browsers.
         * This is the same Firefox UA ref/NewPipe sends; bump it when NewPipe does.
         */
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0"
    }
}
