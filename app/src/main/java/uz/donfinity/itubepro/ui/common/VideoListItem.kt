package uz.donfinity.itubepro.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.domain.model.VideoSummary

/**
 * One row in search results, related videos and library lists: thumbnail on the left, text
 * on the right.
 *
 * @param progress watched fraction (0..1) drawn as a bar on the thumbnail, or null.
 * @param trailing optional action at the end of the row (e.g. a remove button).
 */
@Composable
fun VideoListItem(
    video: VideoSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VideoThumbnail(
            url = video.thumbnailUrl,
            durationSeconds = video.durationSeconds,
            isLive = video.isLive,
            progress = progress,
            modifier = Modifier.width(160.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                video.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                video.uploaderName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(video.viewCount?.let { viewCountText(it) }, video.uploadedText)
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString(stringResource(R.string.meta_separator)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** 16:9 thumbnail with a duration (or LIVE) badge in the corner. */
@Composable
fun VideoThumbnail(
    url: String?,
    durationSeconds: Long?,
    isLive: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    progress: Float? = null,
) {
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        val badge = when {
            isLive -> stringResource(R.string.badge_live)
            durationSeconds != null -> Formatters.duration(durationSeconds)
            else -> null
        }
        if (progress != null && progress > 0f) {
            // Red "already watched" bar along the bottom, like YouTube.
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(Color(0xFFFF0000)),
            )
        }
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(
                        if (isLive) Color(0xFFCC0000) else Color.Black.copy(alpha = 0.75f),
                        RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}
