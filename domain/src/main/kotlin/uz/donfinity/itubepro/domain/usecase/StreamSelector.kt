package uz.donfinity.itubepro.domain.usecase

import uz.donfinity.itubepro.domain.model.MediaStream
import uz.donfinity.itubepro.domain.model.MediaStream.Kind

/**
 * Picks which streams to play. Pure functions with no I/O, so they're easy to unit test.
 *
 * Rules (loosely based on ref/NewPipe/.../util/ListHelper.java defaults):
 * - Video: all qualities of one codec family, so the player can switch between them
 *   (adaptive "Auto" or a manual pick) without re-buffering. A player can only adapt
 *   smoothly within one codec, so we pick the family with the best hardware support that
 *   still has a reasonable resolution: H.264 (avc1, every phone decodes it in hardware),
 *   then VP9, then AV1 (often decoded in software on older phones, which drains the battery).
 * - Audio: the original language track over dubbed/descriptive ones, then highest bitrate.
 * - If there's no separate video track, fall back to a muxed video+audio stream.
 */
object StreamSelector {

    data class Selection(
        /** Sorted from lowest to highest resolution. Empty for audio-only content. */
        val videos: List<MediaStream>,
        /** Null when [videos] holds a single muxed stream (it already includes audio). */
        val audio: MediaStream?,
    )

    fun select(streams: List<MediaStream>): Selection {
        val playable = streams.filter {
            it.delivery == MediaStream.Delivery.PROGRESSIVE || it.delivery == MediaStream.Delivery.DASH
        }
        val audio = selectAudio(playable.filter { it.kind == Kind.AUDIO })
        val videoOnly = selectVideoFamily(playable.filter { it.kind == Kind.VIDEO_ONLY })

        return when {
            videoOnly.isNotEmpty() && audio != null -> Selection(videoOnly, audio)
            else -> {
                // No separate audio: a muxed stream is the only way to get video with sound.
                // Muxed streams are separate files with audio, so only the best one is used.
                val muxed = playable.filter { it.kind == Kind.MUXED }.maxByOrNull { it.height }
                if (muxed != null) Selection(listOf(muxed), audio = null) else Selection(emptyList(), audio)
            }
        }
    }

    private fun selectVideoFamily(candidates: List<MediaStream>): List<MediaStream> {
        if (candidates.isEmpty()) return emptyList()
        val families = candidates.groupBy { codecFamily(it.codec) }
        // Prefer the best-supported codec unless it's much worse in resolution than what
        // another codec offers (e.g. a video only has 360p in H.264 but 1080p in VP9).
        val bestHeight = candidates.maxOf { it.height }
        val family = families.entries
            .sortedByDescending { it.key.rank }
            .firstOrNull { (_, streams) -> streams.maxOf { it.height } >= minOf(bestHeight, ACCEPTABLE_HEIGHT) }
            ?: families.maxBy { (_, streams) -> streams.maxOf { it.height } }
        // Same height may appear twice (e.g. 720p and 720p60). Keep both; the player
        // treats them as separate qualities.
        return family.value.sortedWith(compareBy<MediaStream> { it.height }.thenBy { it.bitrate })
    }

    private fun selectAudio(candidates: List<MediaStream>): MediaStream? =
        candidates.maxWithOrNull(
            compareBy<MediaStream> { audioTrackRank(it.audioTrackType) }.thenBy { it.bitrate },
        )

    private enum class CodecFamily(val rank: Int) { AVC(3), VP9(2), AV1(1), OTHER(0) }

    private fun codecFamily(codec: String?): CodecFamily = when {
        codec == null -> CodecFamily.OTHER
        codec.startsWith("avc1") -> CodecFamily.AVC
        codec.startsWith("vp9") || codec.startsWith("vp09") -> CodecFamily.VP9
        codec.startsWith("av01") -> CodecFamily.AV1
        else -> CodecFamily.OTHER
    }

    private fun audioTrackRank(type: MediaStream.AudioTrackType?): Int = when (type) {
        MediaStream.AudioTrackType.ORIGINAL, null -> 2
        MediaStream.AudioTrackType.SECONDARY, MediaStream.AudioTrackType.DUBBED -> 1
        MediaStream.AudioTrackType.DESCRIPTIVE -> 0
    }

    /** H.264 tops out at 1080p on YouTube. If it reaches this, it's "good enough" to prefer. */
    private const val ACCEPTABLE_HEIGHT = 1080
}
