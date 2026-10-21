package uz.donfinity.itubepro.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.ui.graphics.Color
import uz.donfinity.itubepro.ui.player.VideoPlayer
import uz.donfinity.itubepro.ui.player.rememberPlayWithNotificationPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.domain.model.Description
import uz.donfinity.itubepro.domain.model.VideoDetails
import uz.donfinity.itubepro.ui.common.ErrorState
import uz.donfinity.itubepro.ui.common.Formatters
import uz.donfinity.itubepro.ui.common.LoadingState
import uz.donfinity.itubepro.ui.common.VideoListItem
import uz.donfinity.itubepro.ui.common.VideoThumbnail
import uz.donfinity.itubepro.ui.common.subscriberCountText
import uz.donfinity.itubepro.ui.common.viewCountText

@Composable
fun VideoDetailScreen(
    onBack: () -> Unit,
    onVideoClick: (url: String) -> Unit,
    viewModel: VideoDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val onPlay = rememberPlayWithNotificationPermission(viewModel::onPlay)
    VideoDetailContent(
        state = state,
        playerState = playerState,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        onPlay = onPlay,
        onToggleAudioOnly = viewModel::onToggleAudioOnly,
        onVideoClick = onVideoClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoDetailContent(
    state: VideoDetailUiState,
    playerState: DetailPlayerState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
    onToggleAudioOnly: () -> Unit,
    onVideoClick: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            VideoDetailUiState.Loading -> LoadingState(Modifier.padding(padding))
            is VideoDetailUiState.Error -> ErrorState(
                message = stringResource(state.messageRes),
                onRetry = onRetry,
                modifier = Modifier.padding(padding),
            )
            is VideoDetailUiState.Content -> DetailsList(
                details = state.details,
                playerState = playerState,
                padding = padding,
                onPlay = onPlay,
                onToggleAudioOnly = onToggleAudioOnly,
                onVideoClick = onVideoClick,
            )
        }
    }
}

@Composable
private fun DetailsList(
    details: VideoDetails,
    playerState: DetailPlayerState,
    padding: PaddingValues,
    onPlay: () -> Unit,
    onToggleAudioOnly: () -> Unit,
    onVideoClick: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "player") {
            val player = playerState.player
            if (playerState.isCurrent && !playerState.audioOnly && player != null) {
                VideoPlayer(player)
            } else {
                PlayableThumbnail(details, playerState, onPlay)
            }
        }
        item(key = "header") { Header(details) }
        item(key = "actions") { PlaybackActions(playerState, onPlay, onToggleAudioOnly) }
        item(key = "channel") { ChannelRow(details) }
        if (details.description.content.isNotBlank()) {
            item(key = "description") { DescriptionCard(details.description) }
        }
        if (details.related.isNotEmpty()) {
            item(key = "related_header") {
                Text(
                    stringResource(R.string.detail_related),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(details.related, key = { "related_" + it.url }) { video ->
                VideoListItem(video = video, onClick = { onVideoClick(video.url) })
            }
        }
    }
}

/** Thumbnail with a play button, or an "audio only" note while this video plays without video. */
@Composable
private fun PlayableThumbnail(details: VideoDetails, playerState: DetailPlayerState, onPlay: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        VideoThumbnail(
            url = details.thumbnailUrl,
            durationSeconds = details.durationSeconds,
            isLive = details.isLive,
            cornerRadius = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        if (playerState.isCurrent) {
            Text(
                stringResource(R.string.detail_playing_audio_only),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        } else {
            FilledIconButton(onClick = onPlay, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Filled.PlayArrow, stringResource(R.string.action_play), Modifier.size(36.dp))
            }
        }
    }
}

@Composable
private fun PlaybackActions(playerState: DetailPlayerState, onPlay: () -> Unit, onToggleAudioOnly: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!playerState.isCurrent) {
            Button(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                Text(stringResource(R.string.action_play), Modifier.padding(start = 6.dp))
            }
        }
        FilterChip(
            selected = playerState.audioOnly,
            onClick = onToggleAudioOnly,
            label = { Text(stringResource(R.string.settings_audio_only)) },
            leadingIcon = { Icon(Icons.Outlined.Headphones, contentDescription = null, Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun Header(details: VideoDetails) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(details.title, style = MaterialTheme.typography.titleLarge)
        val meta = listOfNotNull(
            details.viewCount?.let { viewCountText(it) },
            details.uploadDate?.let { Formatters.date(it) },
        )
        if (meta.isNotEmpty()) {
            Text(
                meta.joinToString(stringResource(R.string.meta_separator)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        details.likeCount?.let { likes ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.ThumbUp, stringResource(R.string.detail_likes), Modifier.size(16.dp))
                Text(Formatters.compactCount(likes), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ChannelRow(details: VideoDetails) {
    Column {
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = details.uploaderAvatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        details.uploaderName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (details.uploaderVerified) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            stringResource(R.string.detail_verified),
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                details.subscriberCount?.let {
                    Text(
                        subscriberCountText(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun DescriptionCard(description: Description) {
    // UI-only toggle; rememberSaveable keeps it across rotation.
    var expanded by rememberSaveable { mutableStateOf(false) }
    val linkColor = MaterialTheme.colorScheme.primary
    // Parsing HTML is not free, so do it once per description instead of every recomposition.
    val text = remember(description, linkColor) {
        when (description.format) {
            // fromHtml turns <a href> into LinkAnnotation.Url, which Text opens on tap
            // through LocalUriHandler. No ClickableText needed.
            Description.Format.HTML -> AnnotatedString.fromHtml(
                description.content,
                linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor)),
            )
            Description.Format.MARKDOWN, Description.Format.PLAIN -> AnnotatedString(description.content)
        }
    }
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(if (expanded) R.string.detail_show_less else R.string.detail_show_more))
            }
        }
    }
}

private const val COLLAPSED_LINES = 3
