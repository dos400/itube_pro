package uz.donfinity.itubepro.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import uz.donfinity.itubepro.ui.debug.DebugExtractionScreen

/**
 * Type-safe routes: each destination is a @Serializable type instead of a string like
 * "video/{id}". Navigation serialises arguments into the back stack for us, so they
 * survive process death, and the compiler checks the argument types.
 */
@Serializable
data object DebugRoute

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = DebugRoute) {
        composable<DebugRoute> { DebugExtractionScreen() }
    }
}
