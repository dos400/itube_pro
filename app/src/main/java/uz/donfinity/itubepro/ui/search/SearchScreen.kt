package uz.donfinity.itubepro.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.ui.common.AppendFooter
import uz.donfinity.itubepro.ui.common.ErrorState
import uz.donfinity.itubepro.ui.common.LoadingState
import uz.donfinity.itubepro.ui.common.MessageState
import uz.donfinity.itubepro.ui.common.VideoListItem

@Composable
fun SearchScreen(
    onVideoClick: (url: String) -> Unit,
    onOpenDebug: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    // Pure UI state (is the suggestion panel open?). rememberSaveable keeps it across
    // rotation and process death. Start expanded only if nothing was searched yet.
    var expanded by rememberSaveable { mutableStateOf(viewModel.submittedQuery == null) }
    // Hoisted here so the scroll position survives going to a video and coming back.
    val listState = rememberLazyListState()

    SearchContent(
        query = query,
        expanded = expanded,
        suggestions = suggestions,
        results = results,
        listState = listState,
        onQueryChange = viewModel::onQueryChange,
        onExpandedChange = { expanded = it },
        onSearch = {
            viewModel.onSearch(it)
            expanded = false
        },
        onRetry = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        onRetryAppend = viewModel::onRetryAppend,
        onVideoClick = onVideoClick,
        onOpenDebug = onOpenDebug,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchContent(
    query: String,
    expanded: Boolean,
    suggestions: List<String>,
    results: SearchResultsState,
    listState: LazyListState,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onSearch: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryAppend: () -> Unit,
    onVideoClick: (String) -> Unit,
    onOpenDebug: () -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Space under the floating search bar: status bar + bar height + its outer padding.
    val resultsPadding = PaddingValues(
        top = statusBarTop + SearchBarDefaults.InputFieldHeight + 16.dp,
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )

    Box(Modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
        SearchBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .semantics { traversalIndex = 0f },
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    expanded = expanded,
                    onExpandedChange = onExpandedChange,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = {
                        if (expanded) {
                            IconButton(onClick = { onExpandedChange(false) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                            }
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                onQueryChange("")
                                onExpandedChange(true)
                            }) {
                                Icon(Icons.Default.Close, stringResource(R.string.action_clear))
                            }
                        }
                    },
                )
            },
            expanded = expanded,
            onExpandedChange = onExpandedChange,
        ) {
            SuggestionList(
                suggestions = suggestions,
                onSuggestionClick = onSearch,
                onSuggestionFill = onQueryChange,
            )
        }

        Box(Modifier.fillMaxSize().semantics { traversalIndex = 1f }) {
            when (results) {
                SearchResultsState.Idle -> MessageState(
                    icon = Icons.Outlined.VideoLibrary,
                    message = stringResource(R.string.search_idle),
                    modifier = Modifier.padding(resultsPadding),
                    action = { TextButton(onClick = onOpenDebug) { Text(stringResource(R.string.debug_title)) } },
                )
                SearchResultsState.Loading -> LoadingState(Modifier.padding(resultsPadding))
                is SearchResultsState.Error -> ErrorState(
                    message = stringResource(results.messageRes),
                    onRetry = onRetry,
                    modifier = Modifier.padding(resultsPadding),
                )
                is SearchResultsState.Empty -> MessageState(
                    icon = Icons.Outlined.SearchOff,
                    message = stringResource(R.string.search_empty, results.query),
                    modifier = Modifier.padding(resultsPadding),
                )
                is SearchResultsState.Content -> ResultsList(
                    content = results,
                    listState = listState,
                    contentPadding = resultsPadding,
                    onLoadMore = onLoadMore,
                    onRetryAppend = onRetryAppend,
                    onVideoClick = onVideoClick,
                    onSuggestionClick = onSearch,
                )
            }
        }
    }
}

@Composable
private fun SuggestionList(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    onSuggestionFill: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        items(suggestions) { suggestion ->
            ListItem(
                headlineContent = { Text(suggestion) },
                leadingContent = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingContent = {
                    // Like YouTube: put the suggestion into the field to keep typing.
                    IconButton(onClick = { onSuggestionFill(suggestion) }) {
                        Icon(Icons.Default.NorthWest, stringResource(R.string.search_fill_suggestion))
                    }
                },
                modifier = Modifier.clickable { onSuggestionClick(suggestion) },
            )
        }
    }
}

@Composable
private fun ResultsList(
    content: SearchResultsState.Content,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onLoadMore: () -> Unit,
    onRetryAppend: () -> Unit,
    onVideoClick: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
) {
    // rememberUpdatedState: the effect below runs once, but always calls the newest lambda.
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        // snapshotFlow turns Compose state reads into a Flow. Emit (last visible, total)
        // and ask for more when we're within LOAD_MORE_THRESHOLD of the end. Including
        // the total means it fires again after a page is appended. The ViewModel ignores
        // calls while already loading.
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - LOAD_MORE_THRESHOLD) currentOnLoadMore()
            }
    }

    LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        content.suggestion?.let { suggestion ->
            item(key = "suggestion") {
                val text = if (content.isCorrectedSearch) {
                    stringResource(R.string.search_showing_results_for, suggestion)
                } else {
                    stringResource(R.string.search_did_you_mean, suggestion)
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionClick(suggestion) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        // Stable keys let Compose keep each row's state and animate correctly when pages
        // are appended. URLs are unique because the ViewModel deduplicates them.
        items(content.items, key = { it.url }) { video ->
            VideoListItem(video = video, onClick = { onVideoClick(video.url) })
        }
        if (content.nextPage != null || content.append != AppendState.Idle) {
            item(key = "footer") {
                AppendFooter(
                    // Idle with a next page means a load is about to start, so show the spinner.
                    loading = content.append !is AppendState.Error,
                    errorMessage = (content.append as? AppendState.Error)?.let { stringResource(it.messageRes) },
                    onRetry = onRetryAppend,
                )
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 5
