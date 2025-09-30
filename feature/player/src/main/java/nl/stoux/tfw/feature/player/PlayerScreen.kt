package nl.stoux.tfw.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        // Header area with title/tagline on the left and poster on the right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                val editionNumberStr = liveset?.edition?.number ?: "0" // TODO: Replace fallback when data guaranteed
                val taglineStr = liveset?.edition?.tagLine?.trim().orEmpty()
                Text(
                    text = "TFW #$editionNumberStr",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
                if (taglineStr.isNotEmpty()) {
                    Text(
                        text = taglineStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start
                    )
                }
            }
            // Poster image placeholder (shrunk to 130.dp height)
            Box(
                modifier = Modifier
                    .height(130.dp)
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("Poster", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(12.dp))

        Spacer(Modifier.height(12.dp))
        // Track/Liveset/Artist block
        Column(modifier = Modifier.fillMaxWidth()) {
            if (currentTrack != null) {
                Text(
                    text = currentTrack?.title ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
            Text("Waveform", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        // Time row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = formatTime(positionMs), style = MaterialTheme.typography.labelSmall)
            Text(text = formatTime(durationMs), style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(8.dp))
        // Primary controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { viewModel.skipTrackBackward() }, enabled = canSkipPrevTrack) {
                Icon(imageVector = Icons.Filled.FastRewind, contentDescription = "Previous track in liveset")
            }
            IconButton(onClick = { viewModel.previousLiveset() }) {
                Icon(imageVector = Icons.Filled.SkipPrevious, contentDescription = "Previous track")
            }
            Button(
                onClick = { viewModel.playPause() },
                modifier = Modifier.size(72.dp).clip(CircleShape),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            IconButton(onClick = { viewModel.nextLiveset() }) {
                Icon(imageVector = Icons.Filled.SkipNext, contentDescription = "Next track")
            }
            IconButton(onClick = { viewModel.skipTrackForward() }, enabled = canSkipNextTrack) {
                Icon(imageVector = Icons.Filled.FastForward, contentDescription = "Next track in liveset")
            }
        }

        Spacer(Modifier.height(8.dp))
        Divider()
        Spacer(Modifier.height(8.dp))
        // Secondary controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconToggleButton(checked = shuffleEnabled, onCheckedChange = { viewModel.toggleShuffle() }) {
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
