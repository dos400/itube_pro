package uz.donfinity.itubepro.data.repository

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
 * Turns a video URL into a [PlaybackSource]: extract → pick streams → generate DASH manifests →
 * work out the HTTP headers each stream needs.
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
            if (info.streamType.isLive()) throw AppException.LiveNotSupported()

            // Selection is a pure domain rule; we only translate the result back to the
            // NewPipe streams, whose ItagItem we need for manifest generation.
            val streams = info.allStreams()
            val selection = StreamSelector.select(info.toExtractedVideo().streams)
            val video = selection.video?.let { streams[it.id.toInt()] }
            val audio = selection.audio?.let { streams[it.id.toInt()] }
            if (video == null && audio == null) throw AppException.Unavailable()

            PlaybackSource(
                videoUrl = videoUrl,
                title = info.name,
                uploaderName = info.uploaderName.orEmpty(),
                thumbnailUrl = info.thumbnails.preferredUrl(Image.ResolutionLevel.HIGH),
                durationSeconds = info.duration.takeIf { it > 0 },
                video = video?.toTrack(info),
                audio = audio?.toTrack(info),
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
        const val YOUTUBE_ORIGIN = "https://www.youtube.com"
    }
}
