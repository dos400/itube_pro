package uz.donfinity.itubepro.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.domain.model.ChannelDetails
import uz.donfinity.itubepro.ui.common.AppendFooter
import uz.donfinity.itubepro.ui.common.AppendState
import uz.donfinity.itubepro.ui.common.ErrorState
import uz.donfinity.itubepro.ui.common.LoadMoreEffect
import uz.donfinity.itubepro.ui.common.LoadingState
import uz.donfinity.itubepro.ui.common.VideoListItem
import uz.donfinity.itubepro.ui.common.subscriberCountText

@Composable
fun ChannelScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isSubscribed by viewModel.isSubscribed.collectAsStateWithLifecycle()
    ChannelContent(
        state = state,
        isSubscribed = isSubscribed,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        onRetryAppend = viewModel::onRetryAppend,
        onToggleSubscribe = viewModel::onToggleSubscribe,
        onVideoClick = onVideoClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelContent(
    state: ChannelUiState,
    isSubscribed: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryAppend: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onVideoClick: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (state as? ChannelUiState.Content)?.channel?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            ChannelUiState.Loading -> LoadingState(Modifier.padding(padding))
            is ChannelUiState.Error -> ErrorState(stringResource(state.messageRes), onRetry, Modifier.padding(padding))
            is ChannelUiState.Content -> ChannelList(
                content = state,
                isSubscribed = isSubscribed,
                padding = padding,
                onLoadMore = onLoadMore,
                onRetryAppend = onRetryAppend,
                onToggleSubscribe = onToggleSubscribe,
                onVideoClick = onVideoClick,
            )
        }
    }
}

@Composable
private fun ChannelList(
    content: ChannelUiState.Content,
    isSubscribed: Boolean,
    padding: PaddingValues,
    onLoadMore: () -> Unit,
    onRetryAppend: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onVideoClick: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, onLoadMore)

    LazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        content.channel.bannerUrl?.let { banner ->
            item(key = "banner") {
                AsyncImage(
                    model = banner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(BANNER_ASPECT_RATIO)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        item(key = "header") { ChannelHeader(content.channel, isSubscribed, onToggleSubscribe) }
        item(key = "videos_title") {
            Text(
                stringResource(R.string.channel_videos),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }
        if (content.videos.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.channel_no_videos),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(content.videos, key = { it.url }) { video ->
            VideoListItem(video = video, onClick = { onVideoClick(video.url) })
        }
        if (content.nextPage != null || content.append != AppendState.Idle) {
            item(key = "footer") {
                AppendFooter(
                    loading = content.append !is AppendState.Error,
                    errorMessage = (content.append as? AppendState.Error)?.let { stringResource(it.messageRes) },
                    onRetry = onRetryAppend,
                )
            }
        }
        item(key = "bottom_inset") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun ChannelHeader(channel: ChannelDetails, isSubscribed: Boolean, onToggleSubscribe: () -> Unit) {
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                model = channel.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (channel.verified) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            stringResource(R.string.detail_verified),
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                channel.subscriberCount?.let {
                    Text(
                        subscriberCountText(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (isSubscribed) {
            FilledTonalButton(onClick = onToggleSubscribe, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.channel_subscribed))
            }
        } else {
            Button(onClick = onToggleSubscribe, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.channel_subscribe))
            }
        }
        if (channel.description.isNotBlank()) {
            Text(
                channel.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (descriptionExpanded) Int.MAX_VALUE else DESCRIPTION_COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { descriptionExpanded = !descriptionExpanded }) {
                Text(stringResource(if (descriptionExpanded) R.string.detail_show_less else R.string.detail_show_more))
            }
        }
    }
}

/** YouTube channel banners are very wide (about 6.2:1 on phones). */
private const val BANNER_ASPECT_RATIO = 6.2f
private const val DESCRIPTION_COLLAPSED_LINES = 2
