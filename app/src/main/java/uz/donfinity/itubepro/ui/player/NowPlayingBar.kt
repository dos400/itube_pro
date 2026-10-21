package uz.donfinity.itubepro.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.core.player.NowPlayingState

/**
 * Mini-player above the navigation bar: live video thumbnail (or artwork), title, progress,
 * play/pause and close. Swipe it sideways to stop playback.
 *
 * @param videoPlayer when non-null, the mini-player shows the live video. The caller passes
 *   null while another screen already shows the video, so only one surface is attached.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingBar(
    state: NowPlayingState,
    videoPlayer: Player?,
    progress: Float?,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** False when a bottom navigation bar below already handles the system navigation bar. */
    padForNavigationBar: Boolean = true,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
        onDismiss = { onClose() },
        modifier = modifier,
    ) {
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(if (padForNavigationBar) Modifier.navigationBarsPadding() else Modifier) {
                // Thin progress line on top, like YouTube's mini-player.
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                }
                Row(
                    Modifier
                        .clickable(onClick = onClick)
                        .padding(start = 12.dp, end = 4.dp)
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .height(48.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (videoPlayer != null) {
                            VideoPlayer(player = videoPlayer, useController = false, modifier = Modifier.matchParentSize())
                        } else {
                            AsyncImage(
                                model = state.artworkUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.title.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (state.hasError) stringResource(R.string.player_error_short) else state.artist.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (state.isBuffering) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onPlayPause) {
                            val (icon, label) = when {
                                state.hasError -> Icons.Default.Refresh to R.string.action_retry
                                state.isPlaying -> Icons.Default.Pause to R.string.action_pause
                                else -> Icons.Default.PlayArrow to R.string.action_play
                            }
                            Icon(icon, stringResource(label))
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, stringResource(R.string.action_stop))
                    }
                }
            }
        }
    }
}
