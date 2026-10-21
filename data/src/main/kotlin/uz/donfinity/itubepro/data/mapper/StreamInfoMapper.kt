package uz.donfinity.itubepro.data.mapper

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import uz.donfinity.itubepro.domain.model.ExtractedVideo
import uz.donfinity.itubepro.domain.model.MediaStream

internal fun StreamInfo.toExtractedVideo(): ExtractedVideo = ExtractedVideo(
    id = id,
    url = url,
    title = name,
    uploaderName = uploaderName.orEmpty(),
    durationSeconds = duration,
    dashManifestUrl = dashMpdUrl.takeIf { it.isNotEmpty() },
    hlsManifestUrl = hlsUrl.takeIf { it.isNotEmpty() },
    // NewPipe splits streams into three lists: muxed video+audio (getVideoStreams),
    // video-only (getVideoOnlyStreams) and audio-only (getAudioStreams).
    streams = videoStreams.map { it.toMediaStream() } +
        videoOnlyStreams.map { it.toMediaStream() } +
        audioStreams.map { it.toMediaStream() },
)

// isVideoOnly()/getResolution() are called as methods on purpose: VideoStream also has
// deprecated public fields with the same names, and Kotlin property syntax picks those.
private fun VideoStream.toMediaStream() = MediaStream(
    kind = if (isVideoOnly()) MediaStream.Kind.VIDEO_ONLY else MediaStream.Kind.MUXED,
    delivery = deliveryMethod.toDomain(),
    content = content,
    format = format?.getName(),
    codec = codec,
    qualityLabel = getResolution(),
    bitrate = bitrate,
    height = height,
    audioTrackName = null,
)

private fun AudioStream.toMediaStream() = MediaStream(
    kind = MediaStream.Kind.AUDIO,
    delivery = deliveryMethod.toDomain(),
    content = content,
    format = format?.getName(),
    codec = codec,
    qualityLabel = averageBitrate.takeIf { it != AudioStream.UNKNOWN_BITRATE }
        ?.let { "$it kbps" }.orEmpty(),
    bitrate = bitrate,
    height = 0,
    audioTrackName = audioTrackName,
)

private fun DeliveryMethod.toDomain() = when (this) {
    DeliveryMethod.PROGRESSIVE_HTTP -> MediaStream.Delivery.PROGRESSIVE
    DeliveryMethod.DASH -> MediaStream.Delivery.DASH
    DeliveryMethod.HLS -> MediaStream.Delivery.HLS
    else -> MediaStream.Delivery.OTHER
}
