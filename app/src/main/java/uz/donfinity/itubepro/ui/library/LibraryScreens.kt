package uz.donfinity.itubepro.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.domain.model.Playlist
import uz.donfinity.itubepro.ui.common.ConfirmDialog
import uz.donfinity.itubepro.ui.common.LoadingState
import uz.donfinity.itubepro.ui.common.NameDialog
import uz.donfinity.itubepro.ui.common.SettingsAction
import uz.donfinity.itubepro.ui.common.VideoListItem

// ---- Library (top-level tab) ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenHistory: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    var showNewPlaylist by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = { SettingsAction(onOpenSettings) },
            )
        },
    ) { padding ->
        val data = overview
        if (data == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item(key = "history") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_history)) },
                    supportingContent = { Text(pluralStringResource(R.plurals.video_count, data.historyCount, data.historyCount)) },
                    leadingContent = { Icon(Icons.Outlined.History, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenHistory),
                )
            }
            item(key = "favorites") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_favorites)) },
                    supportingContent = { Text(pluralStringResource(R.plurals.video_count, data.favoritesCount, data.favoritesCount)) },
                    leadingContent = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenFavorites),
                )
            }
            item(key = "subscriptions") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_subscriptions)) },
                    supportingContent = {
                        Text(pluralStringResource(R.plurals.channel_count, data.subscriptionsCount, data.subscriptionsCount))
                    },
                    leadingContent = { Icon(Icons.Outlined.Subscriptions, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenSubscriptions),
                )
            }
            item(key = "playlists_header") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.library_playlists),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showNewPlaylist = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(18.dp))
                        Text(stringResource(R.string.playlist_new), Modifier.padding(start = 4.dp))
                    }
                }
            }
            if (data.playlists.isEmpty()) {
                item(key = "playlists_empty") {
                    Text(
                        stringResource(R.string.library_playlists_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(data.playlists, key = { "playlist_${it.id}" }) { playlist ->
                PlaylistRow(playlist, onClick = { onOpenPlaylist(playlist.id) })
            }
            item(key = "bottom_inset") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }

    if (showNewPlaylist) {
        NameDialog(
            title = stringResource(R.string.playlist_new),
            onConfirm = {
                viewModel.onCreatePlaylist(it)
                showNewPlaylist = false
            },
            onDismiss = { showNewPlaylist = false },
        )
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(pluralStringResource(R.plurals.video_count, playlist.itemCount, playlist.itemCount)) },
        leadingContent = {
            Box(
                Modifier
                    .width(96.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (playlist.thumbnailUrl != null) {
                    AsyncImage(
                        model = playlist.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, contentDescription = null)
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

// ---- History ----

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    LibraryListScaffold(
        title = stringResource(R.string.library_history),
        items = items,
        emptyIcon = Icons.Outlined.History,
        emptyMessage = stringResource(R.string.history_empty),
        onBack = onBack,
        actions = {
            if (!items.isNullOrEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.history_clear))
                }
            }
        },
    ) { list ->
        items(list, key = { it.video.url }) { entry ->
            val durationMs = entry.video.durationSeconds?.times(1000)
            VideoListItem(
                video = entry.video,
                onClick = { onVideoClick(entry.video.url) },
                progress = durationMs?.let { entry.positionMs.toFloat() / it },
                trailing = {
                    IconButton(onClick = { viewModel.onRemove(entry.video.url) }) {
                        Icon(Icons.Default.Close, stringResource(R.string.action_remove))
                    }
                },
            )
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.history_clear),
            message = stringResource(R.string.history_clear_message),
            confirmLabel = stringResource(R.string.history_clear_confirm),
            onConfirm = {
                viewModel.onClear()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}

// ---- Favorites ----

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    LibraryListScaffold(
        title = stringResource(R.string.library_favorites),
        items = items,
        emptyIcon = Icons.Outlined.FavoriteBorder,
        emptyMessage = stringResource(R.string.favorites_empty),
        onBack = onBack,
    ) { list ->
        items(list, key = { it.url }) { video ->
            VideoListItem(
                video = video,
                onClick = { onVideoClick(video.url) },
                trailing = {
                    IconButton(onClick = { viewModel.onRemove(video) }) {
                        Icon(Icons.Filled.Favorite, stringResource(R.string.favorite_remove), tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        }
    }
}

// ---- One playlist ----

@Composable
fun PlaylistScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showRename by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    // Deleted (by us or anywhere else): nothing left to show.
    if (state is PlaylistUiState.Gone) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val content = state as? PlaylistUiState.Content

    LibraryListScaffold(
        title = content?.playlist?.playlist?.name.orEmpty(),
        items = content?.playlist?.items,
        emptyIcon = Icons.AutoMirrored.Outlined.PlaylistPlay,
        emptyMessage = stringResource(R.string.playlist_empty),
        onBack = onBack,
        actions = {
            if (content != null) {
                IconButton(onClick = { showRename = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.playlist_rename)) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.playlist_delete)) }
            }
        },
    ) { list ->
        items(list, key = { it.url }) { video ->
            VideoListItem(
                video = video,
                onClick = { onVideoClick(video.url) },
                trailing = {
                    IconButton(onClick = { viewModel.onRemove(video.url) }) {
                        Icon(Icons.Default.Close, stringResource(R.string.action_remove))
                    }
                },
            )
        }
    }

    if (showRename && content != null) {
        NameDialog(
            title = stringResource(R.string.playlist_rename),
            initialName = content.playlist.playlist.name,
            onConfirm = {
                viewModel.onRename(it)
                showRename = false
            },
            onDismiss = { showRename = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.playlist_delete),
            message = stringResource(R.string.playlist_delete_message),
            confirmLabel = stringResource(R.string.playlist_delete_confirm),
            onConfirm = {
                confirmDelete = false
                viewModel.onDelete() // the state becomes Gone and the screen closes itself
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

// ---- Subscriptions ----

@Composable
fun SubscriptionsScreen(
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    LibraryListScaffold(
        title = stringResource(R.string.library_subscriptions),
        items = items,
        emptyIcon = Icons.Outlined.Subscriptions,
        emptyMessage = stringResource(R.string.subscriptions_empty),
        onBack = onBack,
    ) { list ->
        items(list, key = { it.channelUrl }) { channel ->
            ListItem(
                headlineContent = { Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = {
                    AsyncImage(
                        model = channel.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                },
                trailingContent = {
                    TextButton(onClick = { viewModel.onUnsubscribe(channel.channelUrl) }) {
                        Text(stringResource(R.string.channel_unsubscribe))
                    }
                },
                modifier = Modifier.clickable { onChannelClick(channel.channelUrl) },
            )
        }
    }
}
