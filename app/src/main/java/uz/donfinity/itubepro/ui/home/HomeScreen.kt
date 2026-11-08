package uz.donfinity.itubepro.ui.home

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.domain.model.Recommendations
import uz.donfinity.itubepro.ui.common.ErrorState
import uz.donfinity.itubepro.ui.common.LoadingState
import uz.donfinity.itubepro.ui.common.MessageState
import uz.donfinity.itubepro.ui.common.SettingsAction
import uz.donfinity.itubepro.ui.common.VideoListItem

@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onRetry = viewModel::onRetry,
        onRefresh = viewModel::onRefresh,
        onRefreshErrorShown = viewModel::onRefreshErrorShown,
        onVideoClick = onVideoClick,
        onOpenSettings = onOpenSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshErrorShown: () -> Unit,
    onVideoClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val refreshError = (state as? HomeUiState.Content)?.refreshError
    LaunchedEffect(refreshError) {
        if (refreshError != null) {
            snackbarHostState.showSnackbar(resources.getString(refreshError))
            onRefreshErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = { SettingsAction(onOpenSettings) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (state) {
            HomeUiState.Loading -> LoadingState(Modifier.padding(padding))
            is HomeUiState.Error -> ErrorState(stringResource(state.messageRes), onRetry, Modifier.padding(padding))
            is HomeUiState.Content -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                val recommendations = state.recommendations
                if (recommendations.items.isEmpty()) {
                    // Scrollable so the pull gesture still works on the empty state.
                    MessageState(
                        icon = Icons.Outlined.VideoLibrary,
                        message = stringResource(R.string.home_empty),
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item(key = "header") { HomeHeader(recommendations.source) }
                        items(recommendations.items, key = { it.url }) { video ->
                            VideoListItem(video = video, onClick = { onVideoClick(video.url) })
                        }
                        item(key = "bottom_inset") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(source: Recommendations.Source) {
    val (title, summary) = when (source) {
        Recommendations.Source.PERSONAL -> R.string.home_for_you to R.string.home_for_you_summary
        Recommendations.Source.TRENDING -> R.string.home_trending to R.string.home_trending_summary
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
