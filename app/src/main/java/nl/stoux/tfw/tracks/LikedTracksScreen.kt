package nl.stoux.tfw.tracks

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import nl.stoux.tfw.core.common.network.dto.LikedTrackDto
import nl.stoux.tfw.core.common.network.dto.trackDisplayName
import nl.stoux.tfw.feature.player.util.formatRelativeIso

@Composable
fun LikedTracksScreen(
    onOpenTrack: (slug: String) -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: LikedTracksViewModel = hiltViewModel(),
) {
    val tracks by viewModel.likedTracks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    // VM is activity scoped, so refresh every time the screen opens
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isLoading && tracks.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading && tracks.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                tracks.isEmpty() -> Text(
                    "No liked tracks yet.\nOpen a track from search and tap the heart to save it here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tracks, key = { it.slug }) { track ->
                        LikedTrackRow(
                            track = track,
                            onClick = { onOpenTrack(track.slug) },
                            onPlay = { if (viewModel.playLatest(track)) onOpenPlayer() },
                            onUnlike = { viewModel.unlike(track) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LikedTrackRow(
    track: LikedTrackDto,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onUnlike: () -> Unit,
) {
    val canPlay = track.appearances.isNotEmpty()
    val latest = track.appearances.firstOrNull()
    val meta = listOfNotNull(
        playedInLabel(track.appearances),
        formatRelativeIso(track.likedAt)?.let { "liked $it" },
    ).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onPlay, enabled = canPlay) {
            PlayPill(enabled = canPlay)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                trackDisplayName(track.artist, track.title, track.version),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Where the play button goes
            latest?.let { a ->
                Text(
                    listOfNotNull(a.livesetTitle, editionLabel(a.editionNumber), formatTimestamp(a.timestamp))
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onUnlike) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = "Unlike",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
