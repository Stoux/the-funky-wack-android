package nl.stoux.tfw.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.cast.framework.CastButtonFactory
import androidx.mediarouter.app.MediaRouteButton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.database.entity.TrackEntity
import nl.stoux.tfw.core.common.database.entity.artworkUrl
import nl.stoux.tfw.feature.player.util.formatTime
import nl.stoux.tfw.feature.player.util.shareLiveset
import nl.stoux.tfw.feature.player.waveforms.ZoomableWaveform
import nl.stoux.tfw.feature.player.waveforms.AnimatedZoomableWaveform
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository.AudioQuality
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    isOpen: Boolean,
    modifier: Modifier = Modifier,
    onCloseRequested: () -> Unit = {},
    onGoFullScreen: () -> Unit = {},
) {
    // Collect state
    val liveset by viewModel.currentLiveset.collectAsState()

    var playerControlsContainerHeight by remember { mutableStateOf<Dp?>(null) }
    val density = LocalDensity.current
    val boxPadding = 16.dp

    val showTrackList = (liveset?.tracks?.size ?: 0) > 0
    val showDescription = !liveset?.liveset?.description.isNullOrBlank()
    val showDetails = playerControlsContainerHeight != null && (showTrackList || showDescription)

    // Reset the set container height when the player is closed
    LaunchedEffect(isOpen) {
        if (!isOpen) {
            playerControlsContainerHeight = null
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.72f))
            .let {
                if (playerControlsContainerHeight != null) {
                    it.height(playerControlsContainerHeight!!)
                } else {
                    it
                }
            }
    ) {

        val scrollState = rememberScrollState()
        var lastScrollState by remember { mutableIntStateOf(scrollState.value) }
        val uiScope = rememberCoroutineScope()

        // Watch for scrolling down, when the track list is available we want to open up the sheet to full screen
        LaunchedEffect(scrollState) {
            snapshotFlow { scrollState.value }
                .distinctUntilChanged()
                .filter { scrollState.isScrollInProgress }
                .map { currentState ->
                    val isScrolling = currentState > lastScrollState
                    lastScrollState = currentState
                    isScrolling
                }
                .distinctUntilChanged()
                .collect { isForward ->
                    onGoFullScreen()
                }
        }


        Column(
            modifier = Modifier.verticalScroll(scrollState)
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        if (playerControlsContainerHeight == null) {
                            playerControlsContainerHeight =
                                with(density) { coordinates.size.height.toDp() + 4.dp }
                        }
                    }) {

                BackgroundPoster(
                    posterUrl = liveset?.edition?.artworkUrl,
                    modifier = Modifier.matchParentSize()
                )

                PlayerControls(
                    viewModel = viewModel,
                    boxPadding = boxPadding,
                    showDetails = showDetails,
                    onScrollDown = {
                        uiScope.launch {
                            onGoFullScreen()
                            scrollState.animateScrollTo(scrollState.value + 400)
                        }
                    }
                )

            }

            // Additional details that are shown below the fold, scroll down to view more
            if (showDetails) {
                // Description
                if (showDescription) {
                    LivesetDescription(
                        description = liveset?.liveset?.description ?: "",
                        boxPadding = boxPadding,
                    )
                }

                if (showDescription && showTrackList) {
                    Spacer(Modifier.height(24.dp))
                }

                // Tracklist
                if (showTrackList) {
                    val currentTrack by viewModel.currentTrack.collectAsState()
                    TrackList(
                        tracks = liveset?.tracks ?: emptyList(),
                        currentTrack = currentTrack,
                        boxPadding = boxPadding,
                        seekToTrack = { sec -> viewModel.seekTo(sec * 1000L) }
                    )
                }
            }


        }
    }
}

@Composable
fun BackgroundPoster(posterUrl: String?, modifier: Modifier) {
    // Background poster stretched full-screen
    if (posterUrl.isNullOrBlank()) {
        return
    }

    // Build a gradient for the edges of the poster to have a less hard cutoff
    val overlayColor = Color.Black.copy(alpha = 0.72f)
    val solidBlack = Color.Black

    val scrimBrush = Brush.verticalGradient(
        // Outside 10% of the image will fade to full black
        colorStops = arrayOf(
            0.0f to solidBlack,
            0.05f to overlayColor,
            0.9f to overlayColor,
            1.0f to solidBlack,
        )
    )

    coil.compose.AsyncImage(
        model = posterUrl,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )

    // Heavy gray/black overlay for readability
    Box(
        modifier = modifier
            .background(brush = scrimBrush)
    ) {}
}

@Composable
fun CastAction(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            MediaRouteButton(context).apply {
                // Wire up the selector using Cast SDK helper
                try {
                    CastButtonFactory.setUpMediaRouteButton(context, this)
                } catch (t: Throwable) {
                    // ignore if cast framework not available
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun PlayerControls(
    viewModel: PlayerViewModel,
    boxPadding: Dp,
    showDetails: Boolean,
    onScrollDown: () -> Unit,
) {
    val liveset by viewModel.currentLiveset.collectAsState()

    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val hasCast by viewModel.hasCast.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val canSkipPrevTrack by viewModel.canSkipPrevTrack.collectAsState()
    val canSkipNextTrack by viewModel.canSkipNextTrack.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val bufferedProgress by viewModel.bufferedProgress.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()
    val actualQuality by viewModel.actualQuality.collectAsState()
    val availableQualities by viewModel.availableQualities.collectAsState()
    val allowLossless by viewModel.allowLossless.collectAsState()
    var showQualityDialog by remember { mutableStateOf(false) }
    var showLosslessConfirmDialog by remember { mutableStateOf(false) }


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {

        // Header: top-aligned title and tagline; keep spacer where poster used to be
        val editionNumberStr =
            liveset?.edition?.number ?: "0" // TODO: Replace fallback when data guaranteed
        val taglineStr = liveset?.edition?.tagLine?.trim().orEmpty()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = boxPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TFW #$editionNumberStr",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (taglineStr.isNotEmpty()) {
                Text(
                    text = taglineStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(8.dp))
            // Spacer preserved to keep visual space where the poster was
//            Spacer(Modifier.height(130.dp)) // TODO: Adjust/remove when dedicated header design is finalized
        }

        Spacer(Modifier.height(12.dp))
        // Track/Liveset/Artist block
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = boxPadding)) {
            Text(
                text = liveset?.liveset?.title ?: "",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = liveset?.liveset?.artistName ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(boxPadding))

        // Waveform placeholder with scrubbing slider at the bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(214.dp),
//                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val waveformPeaks = viewModel.waveformPeaks.collectAsState()

            val peaks = waveformPeaks.value
            val trackKey: Any? = liveset?.liveset?.id ?: liveset?.liveset?.audioWaveformUrl

            when {
                peaks != null && peaks.isNotEmpty() -> {
                    // Interactive waveform with animated height collapse/grow
                    AnimatedZoomableWaveform(
                        trackKey = trackKey,
                        fullPeaks = peaks,
                        progress = (progress ?: 0f).coerceIn(0f, 1f),
                        onProgressChange = { p -> viewModel.seekToPercent(p) },
                        modifier = Modifier.matchParentSize(),
                        durationMs = durationMs,
                        bufferedProgress = bufferedProgress
                    )
                }
                peaks != null && peaks.isEmpty() -> {
                    // Loading state → show a small loader centered; keep slider space free
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                else -> {
                    // Unavailable: fall back to Slider
                    Slider(
                        value = (progress ?: 0f).coerceIn(0f, 1f),
                        onValueChange = { viewModel.seekToPercent(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = boxPadding)
        ) {
            Spacer(Modifier.height(8.dp))

            // Time row with current track in the middle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = currentTrack?.title ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(3f)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                        )
                )
                Text(
                    text = formatTime(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))
            // Primary controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { viewModel.skipTrackBackward() },
                    enabled = canSkipPrevTrack
                ) {
                    Icon(
                        imageVector = Icons.Filled.FastRewind,
                        contentDescription = "Previous track in liveset"
                    )
                }
                IconButton(onClick = { viewModel.previousLiveset() }) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous track"
                    )
                }
                Button(
                    onClick = { viewModel.playPause() },
                    enabled = !isBuffering,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isBuffering) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                IconButton(onClick = { viewModel.nextLiveset() }) {
                    Icon(imageVector = Icons.Filled.SkipNext, contentDescription = "Next track")
                }
                IconButton(onClick = { viewModel.skipTrackForward() }, enabled = canSkipNextTrack) {
                    Icon(
                        imageVector = Icons.Filled.FastForward,
                        contentDescription = "Next track in liveset"
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            // Secondary controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconToggleButton(
                        checked = shuffleEnabled,
                        onCheckedChange = { viewModel.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = if (shuffleEnabled) "Shuffle on" else "Shuffle off",
                            tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val context = LocalContext.current
                    val livesetId = liveset?.liveset?.id
                    if (livesetId != null) {
                        IconButton(onClick = {
                            shareLiveset(context, livesetId, positionMs)
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share"
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (true) {
                        CastAction()
                        Spacer(modifier = Modifier.size(4.dp))
                    }

                    // Audio quality button - shows actual quality being played
                    val displayQuality = actualQuality ?: audioQuality
                    val qualityLabel = when (displayQuality) {
                        AudioQuality.LOW -> "LQ"
                        AudioQuality.HIGH -> "HQ"
                        AudioQuality.LOSSLESS -> "WAV"
                        null -> "HQ"
                    }
                    Surface(
                        onClick = { showQualityDialog = true },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = qualityLabel,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.openQueue() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue"
                        )
                    }
                }
            }

            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxWidth()
            ) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Make the button bounce up & down a little bit when it comes into view
                val buttonBounceOffset = remember { Animatable(0f) }
                LaunchedEffect(showDetails) {
                    if (!showDetails) return@LaunchedEffect

                    repeat(3) {
                        buttonBounceOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = keyframes {
                                durationMillis = 900
                                6f at 450 using LinearEasing
                                0f at 900 using LinearEasing
                            }
                        )
                    }

                }

                IconButton(
                    onClick = onScrollDown,
                    modifier = Modifier
                        .alpha(if (showDetails) 1f else 0f)
                        .offset(y = buttonBounceOffset.value.dp),
                    enabled = showDetails
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardDoubleArrowDown,
                        contentDescription = "View more details"
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // Audio quality selection dialog
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Audio Quality") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioQuality.entries.forEach { quality ->
                        val isSelected = quality == (actualQuality ?: audioQuality)
                        val isAvailable = quality in availableQualities
                        // Lossless requires permission
                        val needsLosslessPermission = quality == AudioQuality.LOSSLESS && !allowLossless
                        Surface(
                            onClick = {
                                if (isAvailable) {
                                    if (needsLosslessPermission) {
                                        // Show lossless confirmation first
                                        showQualityDialog = false
                                        showLosslessConfirmDialog = true
                                    } else {
                                        viewModel.setAudioQuality(quality)
                                        showQualityDialog = false
                                    }
                                }
                            },
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isAvailable -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = when (quality) {
                                    AudioQuality.LOW -> "Low Quality (LQ)"
                                    AudioQuality.HIGH -> "High Quality (HQ)"
                                    AudioQuality.LOSSLESS -> "Lossless (WAV)" + if (needsLosslessPermission && isAvailable) " ⚠️" else ""
                                },
                                modifier = Modifier.padding(16.dp),
                                color = when {
                                    !isAvailable -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Lossless confirmation dialog
    if (showLosslessConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLosslessConfirmDialog = false },
            title = { Text("Enable lossless playback?") },
            text = {
                Text(
                    "Lossless WAV files are massive - typically around 1GB per set. " +
                    "This will use significant data and storage.\n\n" +
                    "High Quality (HQ) is indistinguishable from lossless for 99% of listeners " +
                    "and uses a fraction of the bandwidth.\n\n" +
                    "Are you sure you want to enable lossless playback?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.enableLossless()
                        viewModel.setAudioQuality(AudioQuality.LOSSLESS)
                        showLosslessConfirmDialog = false
                    }
                ) {
                    Text("Enable & Play")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLosslessConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LivesetDescription(
    description: String,
    boxPadding: Dp,
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = boxPadding)
    ) {

        Text(
            text = "Description",
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

}

@Composable
fun TrackList(
    tracks: List<TrackEntity>,
    currentTrack: TrackEntity?,
    boxPadding: Dp,
    seekToTrack: (timestamp: Int) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = boxPadding)
    ) {

        Text(
            text = "Tracklist",
            style = MaterialTheme.typography.titleMedium,
        )

        tracks.forEachIndexed { index, track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val timestampSeconds = track.timestampSec
                val formattedTime =
                    if (timestampSeconds == null) null else formatTime(timestampSeconds * 1000L)

                Column(modifier = Modifier.weight(1f)) {
                    val isCurrent = currentTrack?.id == track.id
                    val metaColor =
                        if (isCurrent) Color.Green /* TODO: Use theme */ else MaterialTheme.colorScheme.secondary

                    val metaText = buildString {
                        append("#${track.orderInSet}")
                        if (formattedTime != null) {
                            append(" - ")
                            append(formattedTime)
                        }
                    }
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = metaColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right: play from timestamp button (icon only, no background)
                if (timestampSeconds != null && formattedTime != null) {
                    IconButton(onClick = { seekToTrack(timestampSeconds) }) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play from $formattedTime"
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}