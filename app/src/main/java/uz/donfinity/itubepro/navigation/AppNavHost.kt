package uz.donfinity.itubepro.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable
import uz.donfinity.itubepro.R
import uz.donfinity.itubepro.ui.debug.DebugExtractionScreen
import uz.donfinity.itubepro.ui.detail.VideoDetailScreen
import uz.donfinity.itubepro.ui.feed.FeedScreen
import uz.donfinity.itubepro.ui.library.FavoritesScreen
import uz.donfinity.itubepro.ui.library.HistoryScreen
import uz.donfinity.itubepro.ui.library.LibraryScreen
import uz.donfinity.itubepro.ui.library.PlaylistScreen
import uz.donfinity.itubepro.ui.library.SubscriptionsScreen
import uz.donfinity.itubepro.ui.common.labelRes
import uz.donfinity.itubepro.ui.player.LocalInPictureInPicture
import uz.donfinity.itubepro.ui.player.NowPlayingBar
import uz.donfinity.itubepro.ui.player.NowPlayingViewModel
import uz.donfinity.itubepro.ui.player.VideoPlayer
import uz.donfinity.itubepro.ui.player.rememberIsInPictureInPictureMode
import uz.donfinity.itubepro.ui.search.SearchScreen
import uz.donfinity.itubepro.ui.settings.SettingsScreen

/*
 * Type-safe routes: each destination is a @Serializable type instead of a string like
 * "video/{id}". Navigation serialises arguments into the back stack for us, so they
 * survive process death, and the compiler checks the argument types.
 */

// Top-level destinations (bottom navigation tabs)
@Serializable data object SearchRoute
@Serializable data object FeedRoute
@Serializable data object LibraryRoute

/** [url] is a video URL; ViewModels read it with `savedStateHandle.toRoute<VideoDetailRoute>()`. */
@Serializable data class VideoDetailRoute(val url: String)
@Serializable data object HistoryRoute
@Serializable data object FavoritesRoute
@Serializable data class PlaylistRoute(val id: Long)
@Serializable data object SubscriptionsRoute
@Serializable data object SettingsRoute
@Serializable data object DebugRoute

private enum class TopLevelTab(
    val route: Any,
    val routeClass: KClass<*>,
    @param:StringRes val label: Int,
    val icon: ImageVector,
) {
    SEARCH(SearchRoute, SearchRoute::class, R.string.tab_search, Icons.Filled.Search),
    FEED(FeedRoute, FeedRoute::class, R.string.tab_feed, Icons.Filled.Subscriptions),
    LIBRARY(LibraryRoute, LibraryRoute::class, R.string.tab_library, Icons.Filled.VideoLibrary),
}

/**
 * App shell: the NavHost, the bottom tabs, the global mini-player, the playback error snackbar
 * and the Picture-in-Picture video. Created outside any destination, so its ViewModel lives as
 * long as the Activity.
 */
@Composable
fun AppNavHost(nowPlayingViewModel: NowPlayingViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val nowPlaying by nowPlayingViewModel.state.collectAsStateWithLifecycle()
    val player by nowPlayingViewModel.player.collectAsStateWithLifecycle()
    val progress by nowPlayingViewModel.progress.collectAsStateWithLifecycle()
    val inPip = rememberIsInPictureInPictureMode()
    val snackbarHostState = remember { SnackbarHostState() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    // Tabs show only on the three top-level screens, like most apps.
    val showTabs = TopLevelTab.entries.any { destination.isOn(it.routeClass) }
    // The detail screen of the playing video shows the video itself, so the mini-player
    // shows only artwork there. Only one surface can be attached to a player at a time.
    val onPlayingDetail = backStackEntry?.let { entry ->
        entry.destination.hasRoute<VideoDetailRoute>() && entry.toRoute<VideoDetailRoute>().url == nowPlaying.mediaId
    } == true

    // LocalResources (not LocalContext) so the text follows configuration changes like
    // switching the device language while the app is open.
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        nowPlayingViewModel.errorMessages.collect { snackbarHostState.showSnackbar(resources.getString(it)) }
    }
    LaunchedEffect(Unit) {
        nowPlayingViewModel.skips.collect { segment ->
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.sponsorblock_skipped, resources.getString(segment.category.labelRes())),
                actionLabel = resources.getString(R.string.action_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) nowPlayingViewModel.onUndoSkip(segment)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                AnimatedVisibility(visible = nowPlaying.mediaId != null) {
                    NowPlayingBar(
                        state = nowPlaying,
                        videoPlayer = player?.takeIf { nowPlaying.hasVideo && !onPlayingDetail && !inPip },
                        progress = progress,
                        onClick = {
                            nowPlaying.mediaId?.let { navController.navigate(VideoDetailRoute(it)) { launchSingleTop = true } }
                        },
                        onPlayPause = nowPlayingViewModel::onPlayPause,
                        onClose = nowPlayingViewModel::onClose,
                        padForNavigationBar = !showTabs,
                    )
                }
                if (showTabs) {
                    // NavigationBar pads itself for the system navigation bar.
                    NavigationBar {
                        TopLevelTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = destination.isOn(tab.routeClass),
                                onClick = { navController.navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
                }
            }
        },
        // Screens handle the system bars themselves (each has its own Scaffold/insets).
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        CompositionLocalProvider(LocalInPictureInPicture provides inPip) {
            NavHost(
                navController = navController,
                startDestination = SearchRoute,
                // padding = height of the bottom bar (mini-player and/or tabs, which already
                // cover the navigation bar). consumeWindowInsets tells nested screens not to
                // pad for the navigation bar a second time.
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            ) {
                val openVideo: (String) -> Unit = { navController.navigate(VideoDetailRoute(it)) }
                val back: () -> Unit = { navController.popBackStack() }

                composable<SearchRoute> {
                    SearchScreen(onVideoClick = openVideo, onOpenSettings = { navController.navigate(SettingsRoute) })
                }
                composable<FeedRoute> { FeedScreen(onVideoClick = openVideo) }
                composable<LibraryRoute> {
                    LibraryScreen(
                        onOpenHistory = { navController.navigate(HistoryRoute) },
                        onOpenFavorites = { navController.navigate(FavoritesRoute) },
                        onOpenSubscriptions = { navController.navigate(SubscriptionsRoute) },
                        onOpenPlaylist = { navController.navigate(PlaylistRoute(it)) },
                    )
                }
                composable<VideoDetailRoute> { VideoDetailScreen(onBack = back, onVideoClick = openVideo) }
                composable<HistoryRoute> { HistoryScreen(onBack = back, onVideoClick = openVideo) }
                composable<FavoritesRoute> { FavoritesScreen(onBack = back, onVideoClick = openVideo) }
                composable<PlaylistRoute> { PlaylistScreen(onBack = back, onVideoClick = openVideo) }
                composable<SubscriptionsRoute> { SubscriptionsScreen(onBack = back) }
                composable<SettingsRoute> {
                    SettingsScreen(onBack = back, onOpenDebug = { navController.navigate(DebugRoute) })
                }
                composable<DebugRoute> { DebugExtractionScreen() }
            }
        }
    }

    // PiP: the window is tiny, so show only the video, drawn over everything else. The
    // screens stay composed underneath (keeping their state) but hide their own video.
    val pipPlayer = player
    if (inPip && pipPlayer != null) {
        VideoPlayer(player = pipPlayer, useController = false, modifier = Modifier.fillMaxSize())
    }
}

private fun NavDestination?.isOn(routeClass: KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(routeClass) } == true

/**
 * Standard bottom-tab navigation: one back stack per tab. Switching tabs saves the current
 * tab's stack (scroll positions, open screens) and restores the target's, instead of piling
 * screens on top of each other.
 */
private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
