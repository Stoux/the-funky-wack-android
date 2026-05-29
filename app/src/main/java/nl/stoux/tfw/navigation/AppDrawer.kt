package nl.stoux.tfw.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class AppScreen(val title: String, val icon: ImageVector) {
    Browse("Browse", Icons.Default.Album),
    Favorites("Favorites", Icons.Default.Favorite),
    Playlists("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay),
    Live("Live", Icons.Default.LiveTv),
    Settings("Settings", Icons.Default.Settings),
    Account("Account", Icons.Default.Person),
}

@Composable
fun AppDrawerContent(
    currentScreen: AppScreen,
    isLoggedIn: Boolean,
    userName: String?,
    liveSessionCount: Int,
    onScreenSelected: (AppScreen) -> Unit,
) {
    ModalDrawerSheet {
        // App title
        Text(
            "The Funky Wack",
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            style = MaterialTheme.typography.titleLarge,
        )

        // Main navigation items
        NavigationDrawerItem(
            icon = { Icon(AppScreen.Browse.icon, contentDescription = null) },
            label = { Text(AppScreen.Browse.title) },
            selected = currentScreen == AppScreen.Browse,
            onClick = { onScreenSelected(AppScreen.Browse) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        if (isLoggedIn) {
            NavigationDrawerItem(
                icon = { Icon(AppScreen.Favorites.icon, contentDescription = null) },
                label = { Text(AppScreen.Favorites.title) },
                selected = currentScreen == AppScreen.Favorites,
                onClick = { onScreenSelected(AppScreen.Favorites) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        NavigationDrawerItem(
            icon = { Icon(AppScreen.Playlists.icon, contentDescription = null) },
            label = { Text(AppScreen.Playlists.title) },
            selected = currentScreen == AppScreen.Playlists,
            onClick = { onScreenSelected(AppScreen.Playlists) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        NavigationDrawerItem(
            icon = { Icon(AppScreen.Live.icon, contentDescription = null) },
            label = {
                if (liveSessionCount > 0) {
                    Text("${AppScreen.Live.title} ($liveSessionCount)")
                } else {
                    Text(AppScreen.Live.title)
                }
            },
            selected = currentScreen == AppScreen.Live,
            onClick = { onScreenSelected(AppScreen.Live) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp))
        Spacer(modifier = Modifier.height(8.dp))

        NavigationDrawerItem(
            icon = { Icon(AppScreen.Settings.icon, contentDescription = null) },
            label = { Text(AppScreen.Settings.title) },
            selected = currentScreen == AppScreen.Settings,
            onClick = { onScreenSelected(AppScreen.Settings) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        NavigationDrawerItem(
            icon = { Icon(AppScreen.Account.icon, contentDescription = null) },
            label = {
                if (isLoggedIn && userName != null) {
                    Text(userName)
                } else {
                    Text("Login")
                }
            },
            selected = currentScreen == AppScreen.Account,
            onClick = { onScreenSelected(AppScreen.Account) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
