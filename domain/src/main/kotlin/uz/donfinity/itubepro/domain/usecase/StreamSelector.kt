package uz.donfinity.itubepro.domain.usecase

import uz.donfinity.itubepro.domain.model.MediaStream
import uz.donfinity.itubepro.domain.model.MediaStream.Kind

/**
 * Picks which streams to play. Pure functions with no I/O, so they're easy to unit test.
 *
 * Rules (loosely based on ref/NewPipe/.../util/ListHelper.java defaults):
 * - Video: highest resolution at or below [maxHeight]. At the same height prefer H.264
 *   (avc1), because every Android phone decodes it in hardware, then VP9, then AV1
 *   (often decoded in software on older phones, which drains the battery).
 * - Audio: the original language track over dubbed/descriptive ones, then highest bitrate.
 * - If there's no separate video track, fall back to a muxed video+audio stream.
 */
object StreamSelector {

    const val DEFAULT_MAX_HEIGHT = 720

    data class Selection(val video: MediaStream?, val audio: MediaStream?)

    fun select(streams: List<MediaStream>, maxHeight: Int = DEFAULT_MAX_HEIGHT): Selection {
        val playable = streams.filter {
            it.delivery == MediaStream.Delivery.PROGRESSIVE || it.delivery == MediaStream.Delivery.DASH
        }
        val audio = selectAudio(playable.filter { it.kind == Kind.AUDIO })
        val videoOnly = selectVideo(playable.filter { it.kind == Kind.VIDEO_ONLY }, maxHeight)

        return when {
            videoOnly != null && audio != null -> Selection(videoOnly, audio)
            // No separate audio: a muxed stream is the only way to get video with sound.
            else -> selectVideo(playable.filter { it.kind == Kind.MUXED }, maxHeight)
                ?.let { Selection(video = it, audio = null) }
                ?: Selection(video = null, audio = audio) // audio-only content
        }
    }

    private fun selectVideo(candidates: List<MediaStream>, maxHeight: Int): MediaStream? {
        if (candidates.isEmpty()) return null
        val fitting = candidates.filter { it.height in 1..maxHeight }
        // Nothing small enough (e.g. only 1080p): take the smallest available.
        val pool = fitting.ifEmpty { listOf(candidates.minBy { it.height }) }
        return pool.maxWith(compareBy<MediaStream> { it.height }.thenBy { codecRank(it.codec) }.thenBy { it.bitrate })
    }

    private fun selectAudio(candidates: List<MediaStream>): MediaStream? =
        candidates.maxWithOrNull(
            compareBy<MediaStream> { audioTrackRank(it.audioTrackType) }.thenBy { it.bitrate },
        )

    private fun codecRank(codec: String?): Int = when {
        codec == null -> 0
        codec.startsWith("avc1") -> 3
        codec.startsWith("vp9") || codec.startsWith("vp09") -> 2
        codec.startsWith("av01") -> 1
        else -> 0
    }

    private fun audioTrackRank(type: MediaStream.AudioTrackType?): Int = when (type) {
        MediaStream.AudioTrackType.ORIGINAL, null -> 2
        MediaStream.AudioTrackType.SECONDARY, MediaStream.AudioTrackType.DUBBED -> 1
        MediaStream.AudioTrackType.DESCRIPTIVE -> 0
    }
}
