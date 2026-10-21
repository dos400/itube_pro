package uz.donfinity.itubepro.core.player.media

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import uz.donfinity.itubepro.domain.model.PlaybackSource
import uz.donfinity.itubepro.domain.model.PlaybackTrack

/**
 * ExoPlayer calls [createMediaSource] for every MediaItem in the playlist. Our items carry
 * an already resolved [PlaybackSource] as their tag (see [MediaItemResolver]), so this
 * builds:
 *
 *   MergingMediaSource( DashMediaSource(video manifest), DashMediaSource(audio manifest) )
 *
 * MergingMediaSource plays its children in sync, as if they were one file with two tracks.
 * Idea from ref/NewPipe/.../player/resolver/VideoPlaybackResolver.java. In "audio only" mode
 * the video track is disabled through track selection, and ExoPlayer stops loading its
 * segments.
 */
@OptIn(UnstableApi::class)
internal class YoutubeMediaSourceFactory(context: Context) : MediaSource.Factory {

    /** Used only for items that didn't go through our resolver (plain URIs). */
    private val fallback = DefaultMediaSourceFactory(context)
    private var loadErrorHandlingPolicy: LoadErrorHandlingPolicy = NoRetryOnForbiddenPolicy()

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val source = mediaItem.localConfiguration?.tag as? PlaybackSource
            ?: return fallback.createMediaSource(mediaItem)

        val children = listOfNotNull(source.video, source.audio).map { buildTrackSource(it, mediaItem) }
        return if (children.size == 1) {
            children.single()
        } else {
            // adjustPeriodTimeOffsets: the two files may start at slightly different
            // timestamps; align them. clipDurations: end when the shorter one ends.
            MergingMediaSource(true, true, *children.toTypedArray())
        }
    }

    private fun buildTrackSource(track: PlaybackTrack, mediaItem: MediaItem): MediaSource {
        val manifest = track.dashManifest
        return if (manifest != null) {
            val dataSourceFactory = YoutubeDataSourceFactory(track.httpHeaders, rangeAsQueryParameter = true)
            val parsed = DashManifestParser().parse(track.url.toUri(), manifest.byteInputStream())
            DashMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                .createMediaSource(parsed, mediaItem)
        } else {
            // Progressive: byte ranges go in a normal Range header (NewPipe does the same).
            val dataSourceFactory = YoutubeDataSourceFactory(track.httpHeaders, rangeAsQueryParameter = false)
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                // Keep mediaId/metadata/tag, but point the child at the stream URL.
                .createMediaSource(mediaItem.buildUpon().setUri(track.url).build())
        }
    }

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
        fallback.setDrmSessionManagerProvider(provider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
        loadErrorHandlingPolicy = policy
        fallback.setLoadErrorHandlingPolicy(policy)
        return this
    }

    override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_DASH, C.CONTENT_TYPE_OTHER)
}

/**
 * ExoPlayer's default policy retries failed loads a few times with back-off. Retrying a
 * 403 is pointless (the URL has expired or belongs to another IP) and only delays our
 * re-extraction in [uz.donfinity.itubepro.core.player.PlaybackRecovery], so fail fast.
 */
@OptIn(UnstableApi::class)
private class NoRetryOnForbiddenPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        if (loadErrorInfo.exception.isHttpForbidden()) C.TIME_UNSET else super.getRetryDelayMsFor(loadErrorInfo)
}
