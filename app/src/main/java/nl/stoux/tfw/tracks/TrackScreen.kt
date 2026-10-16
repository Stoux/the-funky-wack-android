package nl.stoux.tfw.tracks

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import nl.stoux.tfw.core.common.network.dto.TrackAppearanceDto
import nl.stoux.tfw.core.common.network.dto.TrackDetailResponse
import nl.stoux.tfw.core.common.network.dto.TrackSong
import nl.stoux.tfw.core.common.network.dto.displayName

/**
 * Track page for [slug]: where it was played, other versions, and a like heart.
 * Shown as an overlay on top of the current app screen by MainActivity.
 */
@Composable
fun TrackScreen(
    slug: String,
    onBack: () -> Unit,
    onOpenTrack: (slug: String) -> Unit,
    onOpenPlayer: () -> Unit,
    onLoginRequested: () -> Unit,
    viewModel: TrackViewModel = hiltViewModel(),
) {
    val pageState by viewModel.state.collectAsState()
    val likedSlugs by viewModel.likedSlugs.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val context = LocalContext.current
    var showLoginHint by remember { mutableStateOf(false) }

    LaunchedEffect(slug) { viewModel.open(slug) }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // Until the VM caught up with a new slug, show loading rather than the previous track
    val status = if (pageState.slug == slug) pageState.status else TrackStatus.Loading

    Box(modifier = Modifier.fillMaxSize()) {
        when (status) {
            TrackStatus.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            TrackStatus.NotFound -> CenteredMessage(
                message = "This track no longer exists.",
                action = "Go back",
                onAction = onBack,
            )
            is TrackStatus.Error -> CenteredMessage(
                message = status.message,
                action = "Retry",
                onAction = viewModel::retry,
            )
            is TrackStatus.Loaded -> TrackContent(
                detail = status.detail,
                isLiked = isLoggedIn && status.detail.track.slug in likedSlugs,
                onToggleLike = { song, liked ->
                    if (isLoggedIn) viewModel.setLiked(song, liked) else showLoginHint = true
                },
                onPlay = { appearance ->
                    viewModel.play(appearance.livesetId, appearance.timestamp)
                    onOpenPlayer()
                },
                onOpenTrack = onOpenTrack,
            )
        }
    }

    if (showLoginHint) {
        AlertDialog(
            onDismissRequest = { showLoginHint = false },
            title = { Text("Log in to like tracks") },
            text = { Text("Liked tracks are saved to your account, so they show up on all your devices.") },
            confirmButton = {
                TextButton(onClick = {
                    showLoginHint = false
                    onLoginRequested()
                }) { Text("Log in") }
            },
            dismissButton = {
                TextButton(onClick = { showLoginHint = false }) { Text("Not now") }
            },
        )
    }
}

@Composable
private fun TrackContent(
    detail: TrackDetailResponse,
    isLiked: Boolean,
    onToggleLike: (TrackSong, Boolean) -> Unit,
    onPlay: (TrackAppearanceDto) -> Unit,
    onOpenTrack: (String) -> Unit,
) {
    val track = detail.track
    val song = TrackSong(track.slug, track.artist, track.title, track.version)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    track.version?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { onToggleLike(song, !isLiked) }) {
                    Icon(
                        if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "appearances-header") {
            SectionHeader(playedInLabel(detail.appearances) ?: "Not played in any set yet")
        }
        itemsIndexed(
            detail.appearances,
            key = { index, a -> "appearance-${a.livesetId}-${a.timestamp}-$index" },
        ) { index, appearance ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            AppearanceRow(appearance, onClick = { onPlay(appearance) })
        }

        if (detail.otherVersions.isNotEmpty()) {
            item(key = "versions") {
                SectionHeader("Other versions")
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    detail.otherVersions.forEach { other ->
                        // Same artist: the title + version tells them apart; otherwise show it in full
                        val label = if (other.artist.equals(track.artist, ignoreCase = true)) {
                            other.title + (other.version?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
                        } else {
                            other.displayName()
                        }
                        SuggestionChip(
                            onClick = { onOpenTrack(other.slug) },
                            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun AppearanceRow(appearance: TrackAppearanceDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayPill()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                appearance.livesetTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                appearance.artistName.takeIf { it.isNotBlank() },
                editionLabel(appearance.editionNumber),
                appearance.editionDate?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (appearance.line.isNotBlank()) {
                Text(
                    appearance.line,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        formatTimestamp(appearance.timestamp)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CenteredMessage(message: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onAction) { Text(action) }
    }
}
