package nl.stoux.tfw.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import nl.stoux.tfw.core.common.database.entity.artworkUrl

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvNowPlayingScreen(
    onBack: () -> Unit,
    viewModel: TvNowPlayingViewModel = hiltViewModel(),
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val waveformPeaks by viewModel.waveformPeaks.collectAsState()
    val currentLiveset by viewModel.currentLiveset.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val canSkipPrevTrack by viewModel.canSkipPrevTrack.collectAsState()
    val canSkipNextTrack by viewModel.canSkipNextTrack.collectAsState()
    val seekTargetProgress by viewModel.seekTargetProgress.collectAsState()

    // OLED mode state
    val isOledModeActive by viewModel.isOledModeActive.collectAsState()
    val oledSettings by viewModel.oledSettings.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()

    // OLED mode controller
    val oledController = rememberOledModeController()

    var showTracklistDialog by remember { mutableStateOf(false) }
    var isWaveformFocused by remember { mutableStateOf(false) }
    val playPauseFocusRequester = remember { FocusRequester() }

    val liveset = currentLiveset?.liveset
    val tracks = currentLiveset?.tracks ?: emptyList()
    val artistName = liveset?.artistName ?: ""
    val artworkUrl = currentLiveset?.edition?.artworkUrl

    // Handle OLED mode transitions
    LaunchedEffect(isOledModeActive) {
        if (isOledModeActive) {
            oledController.enterOledMode(oledSettings)
        } else {
            oledController.exitOledMode()
        }
    }

    // Handle drift animation
    LaunchedEffect(isOledModeActive, oledSettings.driftEnabled) {
        if (isOledModeActive && oledSettings.driftEnabled) {
            oledController.animateDrift(
                maxOffsetX = OledModeController.MAX_DRIFT_X_DP,
                maxOffsetY = OledModeController.MAX_DRIFT_Y_DP
            )
        }
    }

    // Handle color cycling
    LaunchedEffect(isOledModeActive, oledSettings.colorShiftEnabled) {
        if (isOledModeActive && oledSettings.colorShiftEnabled) {
            oledController.animateColorCycle()
        }
    }

    // Request focus on play/pause button when liveset loads
    LaunchedEffect(liveset) {
        if (liveset != null) {
            playPauseFocusRequester.requestFocus()
        }
    }

    // Handle key events for OLED mode - exit on any input
    fun handleInteraction() {
        viewModel.onUserInteraction()
        if (isOledModeActive) {
            viewModel.exitOledMode()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    handleInteraction()
                }
                false // Don't consume the event, let it propagate
            }
            .graphicsLayer {
                alpha = oledController.dimAlpha.value
            }
    ) {
        if (liveset == null) {
            // No playback state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nothing playing",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 32.dp)
            ) {
                // Top section: Artwork + Info + Controls
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // Left: Artwork (fixed width box, poster maintains ratio)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(0.75f), // 3:4 poster ratio container
                        contentAlignment = Alignment.Center
                    ) {
                        if (artworkUrl != null) {
                            DriftingArtwork(
                                imageUrl = artworkUrl,
                                contentDescription = liveset.title,
                                isDrifting = isOledModeActive && oledSettings.driftEnabled,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = liveset.title.take(2).uppercase(),
                                    style = MaterialTheme.typography.displayLarge,
                                    color = Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }

                    // Right: Info + Controls
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Top
                    ) {
                        // Top: Title + Artist (always visible, just dimmed)
                        Box(
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column {
                                // Title with auto-size behavior
                                AutoSizeText(
                                    text = liveset.title,
                                    maxLines = 2,
                                    minFontSize = 20,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Artist
                                if (artistName.isNotBlank()) {
                                    Text(
                                        text = artistName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Edition + tagline (fades out)
                                currentLiveset?.edition?.let { edition ->
                                    Text(
                                        text = buildString {
                                            append("TFW #${edition.number}")
                                            edition.tagLine?.let { append(" - $it") }
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.alpha(oledController.uiVisibility.value)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Middle: Playback controls (fades out in OLED mode)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.alpha(oledController.uiVisibility.value)
                        ) {
                            TvPlaybackControls(
                                isPlaying = isPlaying,
                                canSkipPrevTrack = canSkipPrevTrack,
                                canSkipNextTrack = canSkipNextTrack,
                                isOledModeActive = isOledModeActive,
                                audioQuality = audioQuality,
                                onPlayPause = { viewModel.playPause() },
                                onPreviousLiveset = { viewModel.previousLiveset() },
                                onNextLiveset = { viewModel.nextLiveset() },
                                onPreviousTrack = { viewModel.skipTrackBackward() },
                                onNextTrack = { viewModel.skipTrackForward() },
                                onToggleOledMode = { viewModel.toggleOledMode() },
                                onAudioQualityChange = { viewModel.setAudioQuality(it) },
                                playPauseFocusRequester = playPauseFocusRequester,
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Current track indicator (always visible, dimmed)
                        currentTrack?.let { track ->
                            Text(
                                text = "Now: ${track.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Full track controls (fades out in OLED mode)
                        Box(
                            contentAlignment = Alignment.TopStart,
                            modifier = Modifier.alpha(oledController.uiVisibility.value)
                        ) {
                            if (tracks.isNotEmpty()) {
                                CompactTrackIndicator(
                                    tracks = tracks,
                                    currentTrack = currentTrack,
                                    onViewAll = { showTracklistDialog = true },
                                    onTrackClick = { track -> viewModel.seekToTrack(track) },
                                    upFocusRequester = playPauseFocusRequester,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom section: Full-width waveform
                Column {
                    TvWaveformDisplay(
                        peaks = waveformPeaks,
                        progress = progress,
                        isPlaying = isPlaying,
                        seekTargetProgress = seekTargetProgress,
                        hueShift = if (isOledModeActive && oledSettings.colorShiftEnabled) oledController.waveformHue.value else 0f,
                        onSeekStart = { viewModel.startSeek() },
                        onSeekAdjust = { delta -> viewModel.adjustSeek(delta) },
                        onSeekConfirm = { viewModel.confirmSeek() },
                        onSeekCancel = { viewModel.cancelSeek() },
                        onFocusChanged = { isWaveformFocused = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Time display (always visible, dimmed via parent)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(positionMs),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        // Center: seek target, focus hint, or buffering indicator (fades in OLED mode)
                        Box(modifier = Modifier.alpha(oledController.uiVisibility.value)) {
                            val currentSeekTarget = seekTargetProgress
                            when {
                                currentSeekTarget != null -> {
                                    val seekTargetMs = (currentSeekTarget * durationMs).toLong()
                                    Text(
                                        text = "Seek to ${formatTime(seekTargetMs)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF4CAF50) // Green to match waveform indicator
                                    )
                                }
                                isWaveformFocused -> {
                                    Text(
                                        text = "<<  >> to seek",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                }
                                isBuffering -> {
                                    Text(
                                        text = "Buffering...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                        Text(
                            text = formatTime(durationMs),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Full tracklist dialog
        if (showTracklistDialog && tracks.isNotEmpty()) {
            TracklistDialog(
                tracks = tracks,
                currentTrack = currentTrack,
                onTrackClick = { track ->
                    viewModel.seekToTrack(track)
                    showTracklistDialog = false
                },
                onDismiss = { showTracklistDialog = false }
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AutoSizeText(
    text: String,
    maxLines: Int,
    minFontSize: Int,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var fontSize by remember { mutableStateOf(style.fontSize) }
    var readyToDraw by remember { mutableStateOf(false) }

    Text(
        text = text,
        style = style.copy(fontSize = fontSize),
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.drawWithContent {
            if (readyToDraw) drawContent()
        },
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.didOverflowHeight || textLayoutResult.didOverflowWidth) {
                val newSize = (fontSize.value - 2).coerceAtLeast(minFontSize.toFloat())
                if (newSize >= minFontSize) {
                    fontSize = newSize.sp
                } else {
                    readyToDraw = true
                }
            } else {
                readyToDraw = true
            }
        }
    )
}
