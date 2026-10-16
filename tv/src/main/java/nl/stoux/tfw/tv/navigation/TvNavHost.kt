package nl.stoux.tfw.tv.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import nl.stoux.tfw.tv.ui.auth.TvDeviceCodeScreen
import nl.stoux.tfw.tv.ui.browse.TvBrowseScreen
import nl.stoux.tfw.tv.ui.devices.TvDevicesScreen
import nl.stoux.tfw.tv.ui.browse.TvEditionDetailScreen
import nl.stoux.tfw.tv.ui.favorites.TvFavoritesScreen
import nl.stoux.tfw.tv.ui.live.TvLiveScreen
import nl.stoux.tfw.tv.ui.notifications.TvNotificationsScreen
import nl.stoux.tfw.tv.ui.player.TvNowPlayingScreen
import nl.stoux.tfw.tv.ui.playlists.TvPlaylistDetailScreen
import nl.stoux.tfw.tv.ui.playlists.TvPlaylistsScreen
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
                },
                onFavoritesClick = {
                    navController.navigate(TvDestination.Favorites)
                },
                onPlaylistsClick = {
                    navController.navigate(TvDestination.Playlists)
                },
                onLiveClick = {
                    navController.navigate(TvDestination.Live)
                },
                onLoginClick = {
                    navController.navigate(TvDestination.DeviceCodeLogin)
                },
                onNotificationsClick = {
                    navController.navigate(TvDestination.Notifications)
                },
                onDevicesClick = {
                    navController.navigate(TvDestination.Devices)
                },
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

        composable<TvDestination.DeviceCodeLogin> {
            TvDeviceCodeScreen(
                onAuthorized = {
                    navController.popBackStack()
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<TvDestination.Favorites> {
            TvFavoritesScreen(
                onLivesetClick = { livesetId ->
                    navController.navigate(TvDestination.NowPlaying)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<TvDestination.Playlists> {
            TvPlaylistsScreen(
                onPlaylistClick = { shareCode ->
                    navController.navigate(TvDestination.PlaylistDetail(shareCode))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<TvDestination.PlaylistDetail> {
            TvPlaylistDetailScreen(
                onPlay = { navController.navigate(TvDestination.NowPlaying) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<TvDestination.Live> {
            TvLiveScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<TvDestination.Devices> {
            TvDevicesScreen()
        }

        composable<TvDestination.Notifications> {
            TvNotificationsScreen(
                onOpenPlayer = {
                    navController.navigate(TvDestination.NowPlaying) { launchSingleTop = true }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
