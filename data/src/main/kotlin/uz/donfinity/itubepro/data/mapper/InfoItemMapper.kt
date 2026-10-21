package uz.donfinity.itubepro.data.mapper

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import uz.donfinity.itubepro.domain.model.VideoSummary

/**
 * Keeps only videos. Search (even with the VIDEOS filter) and related lists can also
 * contain channels, playlists or mixes, which we don't support yet.
 */
internal fun List<InfoItem>.toVideoSummaries(): List<VideoSummary> =
    filterIsInstance<StreamInfoItem>().map { it.toVideoSummary() }

internal fun StreamInfoItem.toVideoSummary(): VideoSummary {
    val live = streamType.isLive()
    return VideoSummary(
        url = url,
        title = name,
        uploaderName = uploaderName.orEmpty(),
        thumbnailUrl = thumbnails.preferredUrl(Image.ResolutionLevel.MEDIUM),
        durationSeconds = duration.takeIf { it > 0 && !live },
        viewCount = viewCount.takeIf { it >= 0 },
        uploadedText = textualUploadDate?.takeIf { it.isNotBlank() },
        isLive = live,
    )
}

internal fun StreamType.isLive() =
    this == StreamType.LIVE_STREAM || this == StreamType.AUDIO_LIVE_STREAM

/**
 * YouTube returns each thumbnail in several sizes. Use the requested resolution level if
 * there is one, otherwise the largest image.
 */
internal fun List<Image>.preferredUrl(level: Image.ResolutionLevel): String? =
    (firstOrNull { it.estimatedResolutionLevel == level } ?: maxByOrNull { it.height })?.url
