package nl.stoux.tfw

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
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
import nl.stoux.tfw.feature.browser.EditionListScreen
import nl.stoux.tfw.feature.browser.EditionListViewModel
import nl.stoux.tfw.feature.player.PlayerScreen
import nl.stoux.tfw.feature.player.PlayerViewModel
import nl.stoux.tfw.ui.theme.TheFunkyWackTheme
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import nl.stoux.tfw.service.playback.service.MediaPlaybackService
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import nl.stoux.tfw.core.common.repository.EditionRepository
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : FragmentActivity() {
    private val listViewModel: EditionListViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    @Inject lateinit var queueManager: QueueManager
    @Inject lateinit var editionRepository: EditionRepository

    // Signal from Activity lifecycle (e.g., notification tap) to Compose to open the player
    private val showPlayerRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If launched with action to show player, request it
        if (intent?.action == MediaPlaybackService.ACTION_SHOW_PLAYER) {
            showPlayerRequest.value = true
        }

        enableEdgeToEdge()
        setContent {
            TheFunkyWackTheme {
                val isPlaying by playerViewModel.isPlaying.collectAsState()
                val nowTitle by playerViewModel.nowPlayingTitle.collectAsState()
                val progress by playerViewModel.progress.collectAsState()

                val scaffoldState = rememberBottomSheetScaffoldState()
                val playerIsOpen = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded || scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded
                var playerIsFullScreen by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                // React to external request to open player (e.g., notification tap)
                LaunchedEffect(showPlayerRequest.value) {
                    if (showPlayerRequest.value) {
                        scope.launch { scaffoldState.bottomSheetState.expand() }
                        showPlayerRequest.value = false
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

                BottomSheetScaffold(
                    modifier = Modifier.fillMaxSize(),
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = 0.dp,
                    sheetContainerColor = MaterialTheme.colorScheme.surface,
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                androidx.compose.foundation.layout.Column(
                                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                ) {
                                    Text(text = "The Funky Wack", style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        text = "Wacky beats, the recordings.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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
                                    modifier = Modifier
                                        .fillMaxSize()
                                )
                            } else {
                                EditionListScreen(
                                    viewModel = listViewModel,
                                    onPlayClicked = { mediaId -> playerViewModel.playLiveset(mediaId) },
                                    onOpenPlayer = { scope.launch { scaffoldState.bottomSheetState.expand() } },
                                    onAddToQueue = { id -> queueManager.enqueueNext(id) }
                                )
                            }
                        }
                        val currentLiveset by playerViewModel.currentLiveset.collectAsState()
                        if (isPlaying || nowTitle != null) {
                            val artist = currentLiveset?.liveset?.artistName
                            val editionNo = currentLiveset?.edition?.number
                            val subtitle = listOfNotNull(artist, editionNo?.let { "TFW #$it" }).joinToString(" / ")
                            NowPlayingBar(
                                title = nowTitle ?: "Playing",
                                subtitle = subtitle.takeIf { it.isNotBlank() },
                                isPlaying = isPlaying,
                                progress = progress,
                                onPlayPause = { playerViewModel.playPause() },
                                onOpenPlayer = { scope.launch { scaffoldState.bottomSheetState.expand() } }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == MediaPlaybackService.ACTION_SHOW_PLAYER) {
            showPlayerRequest.value = true
        }
    }
}

@Composable
private fun NowPlayingBar(
    title: String,
    subtitle: String?,
    isPlaying: Boolean,
    progress: Float?,
    onPlayPause: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .background(Color.Black.copy(alpha = 0.72f)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Show progress only when known; otherwise no loading bar to avoid endless spinner
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
                        text = title,
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
                        imageVector = if (isPlaying) androidx.compose.material.icons.Icons.Filled.Pause else androidx.compose.material.icons.Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
            }
        }
    }
}