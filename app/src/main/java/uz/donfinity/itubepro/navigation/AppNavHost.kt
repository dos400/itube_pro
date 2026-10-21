package uz.donfinity.itubepro.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import uz.donfinity.itubepro.ui.debug.DebugExtractionScreen
import uz.donfinity.itubepro.ui.detail.VideoDetailScreen
import uz.donfinity.itubepro.ui.player.NowPlayingBar
import uz.donfinity.itubepro.ui.player.NowPlayingViewModel
import uz.donfinity.itubepro.ui.search.SearchScreen
import uz.donfinity.itubepro.ui.settings.SettingsScreen

/*
 * Type-safe routes: each destination is a @Serializable type instead of a string like
 * "video/{id}". Navigation serialises arguments into the back stack for us, so they
 * survive process death, and the compiler checks the argument types.
 */

@Serializable
data object SearchRoute

/** [url] is a video URL; ViewModels read it with `savedStateHandle.toRoute<VideoDetailRoute>()`. */
@Serializable
data class VideoDetailRoute(val url: String)

@Serializable
data object SettingsRoute

@Serializable
data object DebugRoute

/**
 * App shell: the NavHost plus the global "now playing" bar and playback error snackbar.
 * Created outside any destination, so its ViewModel lives as long as the Activity.
 */
@Composable
fun AppNavHost(nowPlayingViewModel: NowPlayingViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val nowPlaying by nowPlayingViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // LocalResources (not LocalContext) so the text follows configuration changes like
    // switching the device language while the app is open.
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        nowPlayingViewModel.errorMessages.collect { snackbarHostState.showSnackbar(resources.getString(it)) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(visible = nowPlaying.mediaId != null) {
                NowPlayingBar(
                    state = nowPlaying,
                    onClick = {
                        nowPlaying.mediaId?.let { navController.navigate(VideoDetailRoute(it)) { launchSingleTop = true } }
                    },
                    onPlayPause = nowPlayingViewModel::onPlayPause,
                    onClose = nowPlayingViewModel::onClose,
                )
            }
        },
        // Screens handle the system bars themselves (each has its own Scaffold/insets).
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = SearchRoute,
            // padding = height of the now-playing bar (which already covers the navigation
            // bar). consumeWindowInsets tells nested screens not to pad for the navigation
            // bar a second time.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<SearchRoute> {
                SearchScreen(
                    onVideoClick = { navController.navigate(VideoDetailRoute(it)) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                )
            }
            composable<VideoDetailRoute> {
                VideoDetailScreen(
                    onBack = { navController.popBackStack() },
                    onVideoClick = { navController.navigate(VideoDetailRoute(it)) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDebug = { navController.navigate(DebugRoute) },
                )
            }
            composable<DebugRoute> { DebugExtractionScreen() }
        }
    }
}
