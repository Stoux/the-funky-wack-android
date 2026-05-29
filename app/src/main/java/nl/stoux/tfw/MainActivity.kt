package nl.stoux.tfw

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import nl.stoux.tfw.auth.LoginScreen
import nl.stoux.tfw.devices.DevicesScreen
import nl.stoux.tfw.favorites.FavoritesScreen
import nl.stoux.tfw.invites.InvitesScreen
import nl.stoux.tfw.feature.browser.EditionListScreen
import nl.stoux.tfw.feature.browser.EditionListViewModel
import nl.stoux.tfw.feature.player.PlayerScreen
import nl.stoux.tfw.feature.player.PlayerViewModel
import nl.stoux.tfw.live.LiveScreen
import nl.stoux.tfw.navigation.AppDrawerContent
import nl.stoux.tfw.navigation.AppScreen
import nl.stoux.tfw.playlists.PlaylistsScreen
import nl.stoux.tfw.ui.theme.TheFunkyWackTheme
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.service.playback.service.MediaPlaybackService
import nl.stoux.tfw.service.playback.service.session.CustomMediaId
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import nl.stoux.tfw.core.common.repository.EditionRepository
import javax.inject.Inject
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import nl.stoux.tfw.feature.player.util.formatTime
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : FragmentActivity() {
    private val listViewModel: EditionListViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val appLinkViewModel: AppLinkViewModel by viewModels()

    @Inject lateinit var queueManager: QueueManager
    @Inject lateinit var editionRepository: EditionRepository
    @Inject lateinit var authRepository: AuthRepository

    // Signal from Activity lifecycle (e.g., notification tap) to Compose to open the player
    // Using MutableSharedFlow for thread-safe event passing from Activity lifecycle to Compose
    private val showPlayerRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // App update launcher for Play In-App Updates
    private val updateLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { /* User completed or cancelled update */ }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle potential app links first; bounce excluded paths to browser and finish if needed
        if (handleIncomingViewIntent(intent, finishIfBounced = true)) return

        // If launched with action to show player, request it
        if (intent?.action == MediaPlaybackService.ACTION_SHOW_PLAYER) {
            showPlayerRequest.tryEmit(Unit)
        }

        enableEdgeToEdge()
        setContent {
            TheFunkyWackTheme {
                // Check for app updates after a delay on startup
                LaunchedEffect(Unit) {
                    delay(15000)
                    checkForAppUpdate()
                }

                val isPlaying by playerViewModel.isPlaying.collectAsState()
                val nowTitle by playerViewModel.nowPlayingTitle.collectAsState()
                val progress by playerViewModel.progress.collectAsState()
                val isLoggedIn by authRepository.isLoggedIn.collectAsState()
                val currentUser by authRepository.currentUser.collectAsState()

                val scaffoldState = rememberBottomSheetScaffoldState()
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val playerIsOpen = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded || scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded
                var playerIsFullScreen by remember { mutableStateOf(false) }
                var currentScreen by remember { mutableStateOf(AppScreen.Browse) }
                val scope = rememberCoroutineScope()

                // React to external request to open player (e.g., notification tap)
                LaunchedEffect(Unit) {
                    showPlayerRequest.collectLatest {
                        scaffoldState.bottomSheetState.expand()
                    }
                }

                // Always restore the player to normal size when closed
                LaunchedEffect(playerIsOpen) {
                    if (!playerIsOpen) {
                        playerIsFullScreen = false
                    }
                }

                // Handle system back only when the player sheet is open (expanded)
                BackHandler(enabled = playerIsOpen) {
                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                }

                // Handle back when drawer is open
                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }

                // Handle back on non-Browse screens: go back to Browse
                BackHandler(enabled = currentScreen != AppScreen.Browse) {
                    currentScreen = AppScreen.Browse
                }

                // Deep link resolved state from ViewModel
                val resolvedDeepLinkState by appLinkViewModel.resolved.collectAsState(initial = null)

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        AppDrawerContent(
                            currentScreen = currentScreen,
                            isLoggedIn = isLoggedIn,
                            userName = currentUser?.name,
                            liveSessionCount = 0, // TODO: wire up live session count
                            onScreenSelected = { screen ->
                                currentScreen = screen
                                scope.launch { drawerState.close() }
                            },
                        )
                    },
                ) {
                    BottomSheetScaffold(
                        modifier = Modifier.fillMaxSize(),
                        scaffoldState = scaffoldState,
                        sheetPeekHeight = 0.dp,
                        sheetContainerColor = MaterialTheme.colorScheme.surface,
                        topBar = {
                            CenterAlignedTopAppBar(
                                navigationIcon = {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                                    }
                                },
                                title = {
                                    androidx.compose.foundation.layout.Column(
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                    ) {
                                        Text(text = currentScreen.title, style = MaterialTheme.typography.titleLarge)
                                        if (currentScreen == AppScreen.Browse) {
                                            Text(
                                                text = "Wacky beats, the recordings.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                            )
                        },
                        sheetContent = {
                            PlayerScreen(
                                viewModel = playerViewModel,
                                isOpen = playerIsOpen,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .animateContentSize()
                                    .let {
                                        // Let the player take max height if triggered to become full screen
                                        if (playerIsFullScreen) {
                                            it.fillMaxHeight()
                                        } else {
                                            it
                                        }
                                    },
                                onCloseRequested = {
                                    scope.launch {
                                        // Use partialExpand: hide() is not allowed when skipHiddenState is enabled. With peek = 0.dp this is visually hidden.
                                        scaffoldState.bottomSheetState.partialExpand()
                                    }
                                },
                                onGoFullScreen = {
                                    // playerIsFullScreen = true
                                }
                            )
                        }
                    ) { innerPadding ->
                        androidx.compose.foundation.layout.Column(modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                        ) {
                            val showQueue by playerViewModel.showQueue.collectAsState()
                            val queueOpenRequests by playerViewModel.queueOpenRequests.collectAsState()
                            // Close the player sheet when queue opens, including repeated opens
                            LaunchedEffect(queueOpenRequests) {
                                if (showQueue) {
                                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                                }
                            }
                            // Back should close the player first; only close queue if player isn't open
                            BackHandler(enabled = showQueue && !playerIsOpen) { playerViewModel.closeQueue() }

                            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                                if (showQueue) {
                                    nl.stoux.tfw.feature.player.QueueScreen(
                                        queueManager = queueManager,
                                        editionRepository = editionRepository,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    when (currentScreen) {
                                        AppScreen.Browse -> EditionListScreen(
                                            viewModel = listViewModel,
                                            onPlayClicked = { mediaId -> playerViewModel.playLiveset(mediaId) },
                                            onOpenPlayer = { scope.launch { scaffoldState.bottomSheetState.expand() } },
                                            onAddToQueue = { id -> queueManager.enqueueNext(id) }
                                        )
                                        AppScreen.Favorites -> FavoritesScreen(
                                            onPlayClicked = { mediaId -> playerViewModel.playLiveset(CustomMediaId.from(mediaId)) },
                                        )
                                        AppScreen.Playlists -> PlaylistsScreen(
                                            onPlayClicked = { mediaId -> playerViewModel.playLiveset(CustomMediaId.from(mediaId)) },
                                        )
                                        AppScreen.Live -> LiveScreen()
                                        AppScreen.Devices -> DevicesScreen()
                                        AppScreen.Invites -> InvitesScreen()
                                        AppScreen.Settings -> SettingsScreen()
                                        AppScreen.Account -> LoginScreen()
                                    }
                                }
                            }
                            val currentLiveset by playerViewModel.currentLiveset.collectAsState()
                            val currentTrack by playerViewModel.currentTrack.collectAsState()
                            if (isPlaying || nowTitle != null || currentLiveset != null) {
                                NowPlayingBar(
                                    liveset = currentLiveset,
                                    currentTrack = currentTrack,
                                    controllerTitle = nowTitle,
                                    isPlaying = isPlaying,
                                    progress = progress,
                                    onPlayPause = { playerViewModel.playPause() },
                                    onOpenPlayer = { scope.launch { scaffoldState.bottomSheetState.expand() } }
                                )
                            }

                            // Deep-link confirmation dialog via ViewModel
                            val resolved = resolvedDeepLinkState
                            if (resolved != null) {
                                val dl = resolved.deepLink
                                val lwd = resolved.liveset
                                AlertDialog(
                                    onDismissRequest = {
                                        appLinkViewModel.markConsumed()
                                    },
                                    title = {
                                        val at = dl.positionMs?.let { " @ ${formatTime(it)}" } ?: ""
                                        Text("Play \"${lwd.liveset.title}\"$at?")
                                    },
                                    text = {
                                        val tag = lwd.edition.tagLine
                                        val artist = lwd.liveset.artistName
                                        val subtitle = buildString {
                                            append("From TFW #${lwd.edition.number}")
                                            if (!tag.isNullOrBlank()) append(": ").append(tag)
                                            if (artist.isNotBlank()) append("\nby ").append(artist)
                                        }
                                        Text(subtitle)
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            scope.launch {
                                                // Start playback and seek if needed
                                                val start = dl.positionMs ?: 0L
                                                runCatching { queueManager.setContextFromLiveset(lwd.liveset.id, startPositionMs = start, autoplay = true) }
                                                // Open player UI
                                                scaffoldState.bottomSheetState.expand()
                                                appLinkViewModel.markConsumed()
                                            }
                                        }) { Text("Play") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = {
                                            appLinkViewModel.markConsumed()
                                        }) { Text("Not now") }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Handle incoming app links and exclusions
    private fun handleIncomingViewIntent(intent: Intent?, finishIfBounced: Boolean): Boolean {
        val isView = intent?.action == Intent.ACTION_VIEW
        val uri = intent?.takeIf { isView }?.data ?: return false

        if (appLinkViewModel.shouldLetBrowserHandle(uri)) {
            // Forward to browser
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            if (finishIfBounced) {
                finish()
                return true
            }
            return false
        }

        // Parse deep link (nullable) and route to ViewModel; UI will decide what to do
        appLinkViewModel.handleIncomingDeepLink(uri)

        // Prevent re-emission on configuration changes by clearing ACTION_VIEW data
        setIntent(Intent(intent).apply {
            data = null
            action = null
        })

        return false
    }


    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // First, try to handle as an incoming app link; don't finish when activity already running
        handleIncomingViewIntent(intent, finishIfBounced = false)
        if (intent.action == MediaPlaybackService.ACTION_SHOW_PLAYER) {
            showPlayerRequest.tryEmit(Unit)
        }
    }

    private fun checkForAppUpdate() {
        val appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                // Start flexible update flow (downloads in background, less intrusive)
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    updateLauncher,
                    AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE)
                )
            }
        }
    }
}

@Composable
private fun NowPlayingBar(
    liveset: nl.stoux.tfw.core.common.database.dao.LivesetWithDetails?,
    currentTrack: nl.stoux.tfw.core.common.database.entity.TrackEntity?,
    controllerTitle: String?,
    isPlaying: Boolean,
    progress: Float?,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    // Compute display title and subtitle locally based on liveset/track
    val livesetTitle = liveset?.liveset?.title
    val editionNo = liveset?.edition?.number
    val displayTitle = currentTrack?.title ?: livesetTitle ?: controllerTitle ?: "Playing"
    val subtitleParts = mutableListOf<String>()
    if (currentTrack != null && !livesetTitle.isNullOrBlank()) subtitleParts.add(livesetTitle)
    if (editionNo != null) subtitleParts.add("TFW #$editionNo")
    val subtitle = subtitleParts.joinToString(" / ").ifBlank { null }

    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .background(Color.Black.copy(alpha = 0.72f)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val p = progress
            if (p != null) {
                LinearProgressIndicator(
                    progress = { p.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clickable { onOpenPlayer() },
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
            }
        }
    }
}
