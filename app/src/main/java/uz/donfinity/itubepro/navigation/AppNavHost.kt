package uz.donfinity.itubepro.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import uz.donfinity.itubepro.ui.debug.DebugExtractionScreen
import uz.donfinity.itubepro.ui.detail.VideoDetailScreen
import uz.donfinity.itubepro.ui.search.SearchScreen

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
data object DebugRoute

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = SearchRoute) {
        composable<SearchRoute> {
            SearchScreen(
                onVideoClick = { navController.navigate(VideoDetailRoute(it)) },
                onOpenDebug = { navController.navigate(DebugRoute) },
            )
        }
        composable<VideoDetailRoute> {
            VideoDetailScreen(
                onBack = { navController.popBackStack() },
                onVideoClick = { navController.navigate(VideoDetailRoute(it)) },
            )
        }
        composable<DebugRoute> { DebugExtractionScreen() }
    }
}
