package uz.donfinity.itubepro.ui.feed

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.ui.common.Formatters
import uz.donfinity.itubepro.ui.common.LoadingState
import uz.donfinity.itubepro.ui.common.MessageState
import uz.donfinity.itubepro.ui.common.VideoListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onVideoClick: (String) -> Unit,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                is FeedMessage.Error -> resources.getString(message.messageRes)
                is FeedMessage.SomeChannelsFailed ->
                    resources.getQuantityString(R.plurals.feed_channels_failed, message.count, message.count)
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feed_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val current = state
        // Pull down to refresh. The box needs scrollable content to detect the pull, so even
        // the empty state is put in a scrollable column.
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                current == null -> LoadingState()
                !current.hasSubscriptions -> MessageState(
                    icon = Icons.Outlined.Subscriptions,
                    message = stringResource(R.string.feed_no_subscriptions),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
                current.items.isEmpty() -> MessageState(
                    icon = Icons.Outlined.VideoLibrary,
                    message = stringResource(if (refreshing) R.string.feed_loading else R.string.feed_empty),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(current.items, key = { it.video.url }) { item ->
                        // The feed has real dates; show them relative ("3 hours ago").
                        val video = item.uploadedAt?.let { item.video.copy(uploadedText = Formatters.relative(it)) } ?: item.video
                        VideoListItem(video = video, onClick = { onVideoClick(item.video.url) })
                    }
                    item(key = "bottom_inset") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
                }
            }
        }
    }
}
