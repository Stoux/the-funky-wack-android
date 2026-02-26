package nl.stoux.tfw.tv.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import nl.stoux.tfw.tv.ui.browse.TvBrowseScreen
import nl.stoux.tfw.tv.ui.browse.TvEditionDetailScreen
import nl.stoux.tfw.tv.ui.player.TvNowPlayingScreen
import nl.stoux.tfw.tv.ui.settings.TvSettingsScreen

@Composable
fun TvNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = TvDestination.Browse
    ) {
        composable<TvDestination.Browse> {
            TvBrowseScreen(
                onEditionClick = { editionId ->
                    navController.navigate(TvDestination.EditionDetail(editionId))
                },
                onLivesetClick = { livesetId ->
                    navController.navigate(TvDestination.NowPlaying)
                },
                onNowPlayingClick = {
                    navController.navigate(TvDestination.NowPlaying)
                },
                onSettingsClick = {
                    navController.navigate(TvDestination.Settings)
                }
            )
        }

        composable<TvDestination.EditionDetail> { backStackEntry ->
            val route: TvDestination.EditionDetail = backStackEntry.toRoute()
            TvEditionDetailScreen(
                editionId = route.editionId,
                onLivesetClick = { livesetId ->
                    navController.navigate(TvDestination.NowPlaying)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<TvDestination.NowPlaying> {
            TvNowPlayingScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<TvDestination.Settings> {
            TvSettingsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
