package uz.donfinity.itubepro.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** State of loading the *next* page of a list, separate from the first page's state. */
sealed interface AppendState {
    data object Idle : AppendState
    data object Loading : AppendState
    data class Error(@param:StringRes val messageRes: Int) : AppendState
}

/**
 * Infinite scroll: calls [onLoadMore] when the last visible item is within [threshold] of the
 * end. snapshotFlow turns Compose state reads into a Flow. Including the item count makes it
 * fire again after a page is appended. The ViewModel decides whether loading is allowed.
 */
@Composable
fun LoadMoreEffect(listState: LazyListState, onLoadMore: () -> Unit, threshold: Int = 5) {
    // rememberUpdatedState: the effect runs once, but always calls the newest lambda.
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - threshold) currentOnLoadMore()
            }
    }
}
