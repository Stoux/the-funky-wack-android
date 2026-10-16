package nl.stoux.tfw.tv.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import nl.stoux.tfw.tv.ui.common.requestFocusWhenAttached
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvBrowseScreen(
    onEditionClick: (Long) -> Unit,
    onLivesetClick: (Long) -> Unit,
    onNowPlayingClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFavoritesClick: () -> Unit = {},
    onPlaylistsClick: () -> Unit = {},
    onLiveClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onDevicesClick: () -> Unit = {},
    viewModel: TvBrowseViewModel = hiltViewModel(),
) {
    val editions by viewModel.editions.collectAsState()
    val queueState by viewModel.queueState.collectAsState()
    val currentLiveset by viewModel.currentLiveset.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val resumeState by viewModel.resumeState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsState()

    // Back from Notifications lands on its header button again. The flag survives the back stack
    // and is deliberately not an effect key: it's only acted on when this screen is composed again.
    val notificationsFocusRequester = remember { FocusRequester() }
    var returnFocusToNotifications by rememberSaveable { mutableStateOf(false) }
    // Same for the nav row: back lands on the button that was used, not the first one
    val navFocusRequesters = remember { NavItem.entries.associateWith { FocusRequester() } }
    var returnFocusToNav by rememberSaveable { mutableStateOf<NavItem?>(null) }

    LaunchedEffect(editions.isNotEmpty(), isLoggedIn) {
        if (returnFocusToNotifications && editions.isNotEmpty() && isLoggedIn) {
            requestFocusWhenAttached(notificationsFocusRequester)
            returnFocusToNotifications = false
        }
        val navItem = returnFocusToNav
        if (navItem != null && editions.isNotEmpty()) {
            requestFocusWhenAttached(navFocusRequesters.getValue(navItem))
            returnFocusToNav = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (editions.isEmpty()) {
            // Loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()

            // Check if NowPlayingCard or ContinueCard is visible
            val currentItem = queueState.effective.getOrNull(queueState.currentEffectiveIndex)
            val hasNowPlaying = currentItem != null || queueState.isPlaying
            val hasTopCard = hasNowPlaying || resumeState != null

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // App title with settings button
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp, end = 48.dp, top = 48.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = "The Funky Wack",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Wacky beats, the recordings.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLoggedIn) {
                                NotificationsButton(
                                    unreadCount = unreadNotificationCount,
                                    onClick = {
                                        returnFocusToNotifications = true
                                        onNotificationsClick()
                                    },
                                    modifier = Modifier.focusRequester(notificationsFocusRequester),
                                )
                            }
                            IconButton(
                                onClick = onSettingsClick,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // Links to the other screens (only Live and login when logged out)
                item {
                    TvNavRow(
                        isLoggedIn = isLoggedIn,
                        focusRequesters = navFocusRequesters,
                        onClick = { item ->
                            returnFocusToNav = item
                            when (item) {
                                NavItem.Favorites -> onFavoritesClick()
                                NavItem.Playlists -> onPlaylistsClick()
                                NavItem.Live -> onLiveClick()
                                NavItem.Devices -> onDevicesClick()
                                NavItem.Login -> onLoginClick()
                            }
                        },
                    )
                }

                // Now Playing card (if something is playing/paused) OR Continue card (if resume state exists)
                item {
                    if (hasNowPlaying) {
                        NowPlayingCard(
                            queueState = queueState,
                            currentLiveset = currentLiveset,
                            currentTrack = currentTrack,
                            onClick = onNowPlayingClick
                        )
                    } else {
                        resumeState?.let { state ->
                            ContinueCard(
                                resumeState = state,
                                onClick = {
                                    viewModel.resumePlayback()
                                    onNowPlayingClick()
                                }
                            )
                        }
                    }
                }

                // Cross-device "Continue listening" row (only renders when logged in + has positions)
                item {
                    TvContinueListeningRow(
                        onResume = { livesetId, positionSec ->
                            viewModel.resumeLiveset(livesetId, positionSec)
                            onNowPlayingClick()
                        },
                    )
                }

                // Edition rows with poster + liveset list
                items(
                    items = editions,
                    key = { it.edition.id }
                ) { edition ->
                    EditionRow(
                        edition = edition,
                        onLivesetClick = { livesetId ->
                            viewModel.playLiveset(livesetId)
                            onLivesetClick(livesetId)
                        },
                        onHeaderClick = { onEditionClick(edition.edition.id) },
                        onUpPressed = if (edition == editions.firstOrNull() && !hasTopCard) {
                            { coroutineScope.launch { listState.animateScrollToItem(0) } }
                        } else null
                    )
                }

                // Bottom padding
                item {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

private enum class NavItem(val label: String, val icon: ImageVector) {
    Favorites("Favorites", Icons.Default.Favorite),
    Playlists("Playlists", Icons.AutoMirrored.Filled.QueueMusic),
    Live("Live", Icons.Default.Sensors),
    Devices("Devices", Icons.Default.Devices),
    Login("Log in", Icons.AutoMirrored.Filled.Login),
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvNavRow(
    isLoggedIn: Boolean,
    focusRequesters: Map<NavItem, FocusRequester>,
    onClick: (NavItem) -> Unit,
) {
    val items = if (isLoggedIn) {
        listOf(NavItem.Favorites, NavItem.Playlists, NavItem.Live, NavItem.Devices)
    } else {
        listOf(NavItem.Login, NavItem.Live)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { item ->
            TvNavButton(
                label = item.label,
                icon = item.icon,
                onClick = { onClick(item) },
                modifier = Modifier.focusRequester(focusRequesters.getValue(item)),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvNavButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/** Bell with an unread badge, styled like the Settings icon button next to it. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NotificationsButton(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = if (unreadCount > 0) "Notifications ($unreadCount unread)" else "Notifications",
                modifier = Modifier.size(28.dp)
            )
        }
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
