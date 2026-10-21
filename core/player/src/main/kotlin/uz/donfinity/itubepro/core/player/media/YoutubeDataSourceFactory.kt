package uz.donfinity.itubepro.core.player.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import java.util.concurrent.atomic.AtomicLong

/**
 * Makes Media3's stock [DefaultHttpDataSource] talk to YouTube's `/videoplayback` servers
 * the way YouTube clients do.
 *
 * ref/NewPipe forked the whole HTTP data source for this
 * (player/datasource/YoutubeHttpDataSource.java, ~900 lines). We get the same effect with a
 * [ResolvingDataSource], which lets us rewrite each request (DataSpec) just before it is sent:
 * - `rn=N`: a request counter that real clients send.
 * - `range=start-end` query parameter instead of a `Range` header (only for DASH segments,
 *   like NewPipe). The server then answers with exactly those bytes, so the request is
 *   rewritten to start at 0 with no Range header.
 * - POST with the 2-byte body `x\0`, the method current YouTube clients use.
 *
 * One factory per track, because each track may need different headers ([headers] comes
 * from the data layer, which knows which YouTube client the URL belongs to).
 *
 * For HLS (live) NewPipe turns off both `rn` and the range parameter
 * (ref/NewPipe/.../player/helper/PlayerDataSource.java, ytHlsCacheDataSourceFactory).
 */
@OptIn(UnstableApi::class)
internal class YoutubeDataSourceFactory(
    headers: Map<String, String>,
    private val rangeAsQueryParameter: Boolean,
    private val addRequestNumber: Boolean = true,
) : DataSource.Factory {

    private val requestNumber = AtomicLong(0)

    private val upstream = DefaultHttpDataSource.Factory()
        .setUserAgent(headers[USER_AGENT])
        .setDefaultRequestProperties(headers - USER_AGENT)
        .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
        .setReadTimeoutMs(READ_TIMEOUT_MS)
        // We send POST; without this a 302 redirect would silently turn it into GET.
        .setKeepPostFor302Redirects(true)

    override fun createDataSource(): DataSource =
        ResolvingDataSource(upstream.createDataSource(), ::rewrite)

    private fun rewrite(spec: DataSpec): DataSpec {
        if (spec.uri.path?.startsWith("/videoplayback") != true) return spec

        val url = StringBuilder(spec.uri.toString())
        // DASH manifests sometimes already contain rn; don't add it twice.
        if (addRequestNumber && !url.contains(RN_PARAM)) {
            url.append(RN_PARAM).append(requestNumber.getAndIncrement())
        }

        val builder = spec.buildUpon()
            .setHttpMethod(DataSpec.HTTP_METHOD_POST)
            .setHttpBody(POST_BODY)

        val hasRange = spec.position != 0L || spec.length != LENGTH_UNSET
        if (rangeAsQueryParameter && hasRange) {
            url.append("&range=").append(spec.position).append('-')
            if (spec.length != LENGTH_UNSET) url.append(spec.position + spec.length - 1)
            // The URL now selects the bytes. Reset the spec so no Range header is sent
            // and the response body is read from its first byte.
            builder.setPosition(0).setLength(LENGTH_UNSET)
        }
        return builder.setUri(url.toString()).build()
    }

    private companion object {
        const val USER_AGENT = "User-Agent"
        // C.LENGTH_UNSET is a Java int; DataSpec.length is a long.
        const val LENGTH_UNSET = C.LENGTH_UNSET.toLong()
        const val RN_PARAM = "&rn="
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        val POST_BODY = byteArrayOf(0x78, 0)
    }
}
