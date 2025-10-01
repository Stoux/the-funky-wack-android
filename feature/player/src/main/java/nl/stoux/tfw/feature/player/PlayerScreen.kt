package nl.stoux.tfw.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onCloseRequested: () -> Unit = {},
) {
    // Collect state
    val appTitle by viewModel.appTitle.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val hasCast by viewModel.hasCast.collectAsState()
    val liveset by viewModel.currentLiveset.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val canSkipPrevTrack by viewModel.canSkipPrevTrack.collectAsState()
    val canSkipNextTrack by viewModel.canSkipNextTrack.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()

    Box(modifier = modifier) {
        // Background poster stretched full-screen
        val bgPosterUrl = liveset?.edition?.posterUrl
        if (!bgPosterUrl.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model = bgPosterUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }
        // Heavy gray/black overlay for readability
        Box(modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.72f))) {}

        // Foreground content split: top header and bottom-aligned controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: top-aligned title and tagline; keep spacer where poster used to be
            val editionNumberStr =
                liveset?.edition?.number ?: "0" // TODO: Replace fallback when data guaranteed
            val taglineStr = liveset?.edition?.tagLine?.trim().orEmpty()
            Column(
                modifier = Modifier.fillMaxWidth(),
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
            Column(modifier = Modifier.fillMaxWidth()) {
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

            Spacer(Modifier.height(16.dp))
            // Waveform placeholder with scrubbing slider at the bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(214.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Waveform",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val p = progress ?: 0f
                Slider(
                    value = p,
                    onValueChange = { viewModel.seekToPercent(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }

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
                    modifier = Modifier.weight(3f).basicMarquee(
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
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasCast) {
                        IconButton(onClick = { /* TODO: Implement cast */ }) {
                            Icon(imageVector = Icons.Filled.Cast, contentDescription = "Cast")
                        }
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                    IconButton(onClick = { viewModel.openQueue() }) {
                        Icon(imageVector = Icons.Filled.QueueMusic, contentDescription = "Queue")
                    }
                }
            }
            Divider()
            Spacer(Modifier.height(8.dp))

        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val h = (totalSeconds / 3600).toInt()
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
