package nl.stoux.tfw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val listViewModel: EditionListViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TheFunkyWackTheme {
                val isPlaying by playerViewModel.isPlaying.collectAsState()
                val nowTitle by playerViewModel.nowPlayingTitle.collectAsState()
                val progress by playerViewModel.progress.collectAsState()

                val scaffoldState = rememberBottomSheetScaffoldState()
                val scope = rememberCoroutineScope()

                // Handle system back only when the player sheet is open (expanded)
                BackHandler(enabled = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded || scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded) {
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
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                            onCloseRequested = {
                                scope.launch {
                                    // Use partialExpand: hide() is not allowed when skipHiddenState is enabled. With peek = 0.dp this is visually hidden.
                                    scaffoldState.bottomSheetState.partialExpand()
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    androidx.compose.foundation.layout.Column(modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                    ) {
                        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                            EditionListScreen(
                                viewModel = listViewModel,
                                onPlayClicked = { mediaId -> playerViewModel.playLiveset(mediaId) },
                                onOpenPlayer = { scope.launch { scaffoldState.bottomSheetState.expand() } }
                            )
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
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
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