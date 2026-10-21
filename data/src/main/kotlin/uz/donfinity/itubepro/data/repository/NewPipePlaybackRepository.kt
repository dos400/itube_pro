package uz.donfinity.itubepro.data.repository

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import uz.donfinity.itubepro.data.cache.StreamInfoCache
import uz.donfinity.itubepro.data.di.IoDispatcher
import uz.donfinity.itubepro.data.mapper.DashManifestMerger
import uz.donfinity.itubepro.data.mapper.allStreams
import uz.donfinity.itubepro.data.mapper.isLive
import uz.donfinity.itubepro.data.mapper.preferredUrl
import uz.donfinity.itubepro.data.mapper.toExtractedVideo
import uz.donfinity.itubepro.data.network.OkHttpDownloader
import uz.donfinity.itubepro.domain.error.AppException
import uz.donfinity.itubepro.domain.model.PlaybackSource
import uz.donfinity.itubepro.domain.model.PlaybackTrack
import uz.donfinity.itubepro.domain.repository.PlaybackRepository
import uz.donfinity.itubepro.domain.usecase.StreamSelector

/**
 * Turns a video URL into a [PlaybackSource]: extract → pick streams → generate DASH manifests
 * (all video qualities merged into one) → work out the HTTP headers each stream needs.
 */
class NewPipePlaybackRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val cache: StreamInfoCache,
) : PlaybackRepository {

    private val youtube = ServiceList.YouTube

    override suspend fun resolve(videoUrl: String, forceRefresh: Boolean): PlaybackSource =
        extract(ioDispatcher) {
            if (!youtube.streamLHFactory.acceptUrl(videoUrl)) throw AppException.InvalidUrl()
            if (forceRefresh) cache.remove(videoUrl)
            val info = cache.get(videoUrl)
                ?: StreamInfo.getInfo(youtube, videoUrl).also { cache.put(videoUrl, it) }

            // Live streams only come with an HLS manifest (verified in Phase 2).
            if (info.streamType.isLive()) return@extract info.toLiveSource(videoUrl)

            // Selection is a pure domain rule; we only translate the result back to the
            // NewPipe streams, whose ItagItem we need for manifest generation.
            val streams = info.allStreams()
            val selection = StreamSelector.select(info.toExtractedVideo().streams)
            val videos = selection.videos.map { streams[it.id.toInt()] }
            val audio = selection.audio?.let { streams[it.id.toInt()] }
            if (videos.isEmpty() && audio == null) throw AppException.Unavailable()

            PlaybackSource(
                videoUrl = videoUrl,
                title = info.name,
                uploaderName = info.uploaderName.orEmpty(),
                thumbnailUrl = info.thumbnails.preferredUrl(Image.ResolutionLevel.HIGH),
                durationSeconds = info.duration.takeIf { it > 0 },
                video = videos.toVideoTrack(info),
                audio = audio?.toTrack(info),
            )
        }

    private fun StreamInfo.toLiveSource(videoUrl: String): PlaybackSource {
        val hls = hlsUrl.takeIf { it.isNotEmpty() } ?: throw AppException.LiveNotSupported()
        return PlaybackSource(
            videoUrl = videoUrl,
            title = name,
            uploaderName = uploaderName.orEmpty(),
            thumbnailUrl = thumbnails.preferredUrl(Image.ResolutionLevel.HIGH),
            durationSeconds = null,
            video = null,
            audio = null,
            liveHls = PlaybackTrack(url = hls, dashManifest = null, httpHeaders = headersFor(hls), label = "live HLS"),
        )
    }

    /**
     * All selected qualities as one track. Their manifests are merged so the player can switch
     * quality without re-buffering. Streams whose manifest can't be generated are dropped. If
     * none works, play the best one as a progressive file.
     */
    private fun List<Stream>.toVideoTrack(info: StreamInfo): PlaybackTrack? {
        if (isEmpty()) return null
        val withManifest = mapNotNull { stream -> createDashManifest(stream, info.duration)?.let { stream to it } }
        if (withManifest.isEmpty()) return last().toTrack(info) // sorted low → high
        val (lowest, _) = withManifest.first()
        val highest = withManifest.last().first as VideoStream
        val merged = runCatching { DashManifestMerger.merge(withManifest.map { it.second }) }
            .getOrElse { e ->
                // Still playable, just without quality switching. Log it: a silent fallback
                // once hid an Android-only XML parser difference (see CLAUDE.md, Phase 4).
                Log.w(TAG, "Merging ${withManifest.size} video manifests failed; using the best one only", e)
                return withManifest.last().first.toTrack(info)
            }
        return PlaybackTrack(
            url = lowest.content,
            dashManifest = merged,
            // All qualities come from the same YouTube client, so they share headers.
            httpHeaders = headersFor(lowest.content),
            label = "${withManifest.size} qualities up to ${highest.getResolution()} ${highest.codec}",
        )
    }

    private fun Stream.toTrack(info: StreamInfo) = PlaybackTrack(
        url = content,
        dashManifest = createDashManifest(this, info.duration),
        httpHeaders = headersFor(content),
        label = when (this) {
            is VideoStream -> "${getResolution()} $codec"
            is AudioStream -> "${averageBitrate}kbps $codec"
            else -> format?.getName().orEmpty()
        },
    )

    /**
     * Same strategy as ref/NewPipe/.../player/resolver/PlaybackResolver.java
     * (createYoutubeMediaSourceOfVideoStreamType):
     * - video-only / audio progressive streams → generated DASH manifest using the itag's
     *   init/index byte ranges, so the player fetches small segments;
     * - "OTF" streams (DASH delivery without a manifest) → OTF manifest;
     * - muxed legacy streams → null (play as a plain progressive file).
     * If generation fails we fall back to progressive playback instead of failing.
     */
    private fun createDashManifest(stream: Stream, durationSeconds: Long): String? {
        val itag = stream.itagItem ?: return null
        val separateTrack = stream is AudioStream || (stream is VideoStream && stream.isVideoOnly())
        return runCatching {
            when (stream.deliveryMethod) {
                DeliveryMethod.PROGRESSIVE_HTTP -> if (separateTrack) {
                    YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(stream.content, itag, durationSeconds)
                } else {
                    null
                }
                DeliveryMethod.DASH -> YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(stream.content, itag, durationSeconds)
                else -> null
            }
        }.getOrNull()
    }

    /**
     * YouTube checks that the requesting "client" matches the one the URL was issued to
     * (the `c=` query parameter). Idea from ref/NewPipe/.../player/datasource/YoutubeHttpDataSource.java
     * (makeConnection): visionOS URLs need the visionOS User-Agent, web URLs need
     * Origin/Referer like a browser would send.
     */
    private fun headersFor(url: String): Map<String, String> = buildMap {
        if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
            put("User-Agent", YoutubeParsingHelper.getVisionOsUserAgent(null))
        } else {
            put("User-Agent", OkHttpDownloader.USER_AGENT)
        }
        if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
            put("Origin", YOUTUBE_ORIGIN)
            put("Referer", YOUTUBE_ORIGIN)
            put("Sec-Fetch-Dest", "empty")
            put("Sec-Fetch-Mode", "cors")
            put("Sec-Fetch-Site", "cross-site")
        }
    }

    private companion object {
        const val TAG = "PlaybackRepository"
        const val YOUTUBE_ORIGIN = "https://www.youtube.com"
    }
}
