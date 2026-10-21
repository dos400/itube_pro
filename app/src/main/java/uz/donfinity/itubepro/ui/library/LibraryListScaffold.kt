package uz.donfinity.itubepro.ui.library

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.ui.common.LoadingState
import uz.donfinity.itubepro.ui.common.MessageState

/**
 * Shared layout of the library list screens: top bar with back + actions, then a loading
 * spinner, an empty message, or the list itself.
 *
 * @param items null while the first database read is still running.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LibraryListScaffold(
    title: String,
    items: List<T>?,
    emptyIcon: ImageVector,
    emptyMessage: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.(List<T>) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = actions,
            )
        },
    ) { padding ->
        when {
            items == null -> LoadingState(Modifier.padding(padding))
            items.isEmpty() -> MessageState(emptyIcon, emptyMessage, Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) { content(items) }
        }
    }
}
