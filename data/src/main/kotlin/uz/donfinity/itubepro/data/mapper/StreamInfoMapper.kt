package uz.donfinity.itubepro.data.mapper

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.Description as NewPipeDescription
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import uz.donfinity.itubepro.domain.model.Description
import uz.donfinity.itubepro.domain.model.ExtractedVideo
import uz.donfinity.itubepro.domain.model.MediaStream
import uz.donfinity.itubepro.domain.model.VideoDetails

internal fun StreamInfo.toExtractedVideo(): ExtractedVideo = ExtractedVideo(
    id = id,
    url = url,
    title = name,
    uploaderName = uploaderName.orEmpty(),
    durationSeconds = duration,
    dashManifestUrl = dashMpdUrl.takeIf { it.isNotEmpty() },
    hlsManifestUrl = hlsUrl.takeIf { it.isNotEmpty() },
    streams = allStreams().mapIndexed { index, stream -> stream.toMediaStream(index.toString()) },
)

/**
 * NewPipe splits streams into three lists: muxed video+audio (getVideoStreams), video-only
 * (getVideoOnlyStreams) and audio-only (getAudioStreams). We flatten them in a fixed order,
 * so a MediaStream's [MediaStream.id] (its index here) leads back to the NewPipe stream.
 */
internal fun StreamInfo.allStreams(): List<Stream> = videoStreams + videoOnlyStreams + audioStreams

private fun Stream.toMediaStream(id: String): MediaStream = when (this) {
    is VideoStream -> toMediaStream(id)
    is AudioStream -> toMediaStream(id)
    else -> error("Unexpected stream type ${this::class.simpleName}")
}

internal fun StreamInfo.toVideoDetails(): VideoDetails {
    val live = streamType.isLive()
    return VideoDetails(
        url = url,
        title = name,
        thumbnailUrl = thumbnails.preferredUrl(Image.ResolutionLevel.HIGH),
        uploaderName = uploaderName.orEmpty(),
        uploaderAvatarUrl = uploaderAvatars.preferredUrl(Image.ResolutionLevel.LOW),
        uploaderVerified = isUploaderVerified,
        // The extractor uses -1 for "unknown/hidden".
        subscriberCount = uploaderSubscriberCount.takeIf { it >= 0 },
        viewCount = viewCount.takeIf { it >= 0 },
        likeCount = likeCount.takeIf { it >= 0 },
        uploadDate = uploadDate?.instant,
        durationSeconds = duration.takeIf { it > 0 && !live },
        isLive = live,
        description = description.toDomain(),
        related = relatedItems.toVideoSummaries(),
    )
}

private fun NewPipeDescription?.toDomain(): Description {
    if (this == null) return Description("", Description.Format.PLAIN)
    val format = when (type()) {
        NewPipeDescription.Type.HTML -> Description.Format.HTML
        NewPipeDescription.Type.MARKDOWN -> Description.Format.MARKDOWN
        else -> Description.Format.PLAIN
    }
    return Description(content().orEmpty(), format)
}

// isVideoOnly()/getResolution() are called as methods on purpose: VideoStream also has
// deprecated public fields with the same names, and Kotlin property syntax picks those.
private fun VideoStream.toMediaStream(id: String) = MediaStream(
    id = id,
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

private fun AudioStream.toMediaStream(id: String) = MediaStream(
    id = id,
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
    audioTrackType = when (audioTrackType) {
        AudioTrackType.ORIGINAL -> MediaStream.AudioTrackType.ORIGINAL
        AudioTrackType.DUBBED -> MediaStream.AudioTrackType.DUBBED
        AudioTrackType.DESCRIPTIVE -> MediaStream.AudioTrackType.DESCRIPTIVE
        AudioTrackType.SECONDARY -> MediaStream.AudioTrackType.SECONDARY
        null -> null
    },
)

private fun DeliveryMethod.toDomain() = when (this) {
    DeliveryMethod.PROGRESSIVE_HTTP -> MediaStream.Delivery.PROGRESSIVE
    DeliveryMethod.DASH -> MediaStream.Delivery.DASH
    DeliveryMethod.HLS -> MediaStream.Delivery.HLS
    else -> MediaStream.Delivery.OTHER
}
