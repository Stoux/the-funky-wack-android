package nl.stoux.tfw.tv.ui.player

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.stoux.tfw.core.common.database.entity.TrackEntity

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CompactTrackIndicator(
    tracks: List<TrackEntity>,
    currentTrack: TrackEntity?,
    onViewAll: () -> Unit,
    onTrackClick: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier,
    upFocusRequester: FocusRequester? = null,
) {
    val currentIndex = tracks.indexOfFirst { it.id == currentTrack?.id }
    val prevTrack = if (currentIndex > 0) tracks[currentIndex - 1] else null
    val nextTrack = if (currentIndex >= 0 && currentIndex < tracks.size - 1) tracks[currentIndex + 1] else null

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track list (vertical) - always show all 3 rows
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Prev row - navigates up to play/pause
            CompactTrackRow(
                track = prevTrack,
                label = "Prev",
                onClick = { prevTrack?.let { onTrackClick(it) } },
                upFocusRequester = upFocusRequester,
            )

            // Now row
            CompactTrackRow(
                track = currentTrack,
                label = "Now",
                isCurrent = true,
                onClick = { }
            )

            // Next row
            CompactTrackRow(
                track = nextTrack,
                label = "Next",
                onClick = { nextTrack?.let { onTrackClick(it) } }
            )
        }

        // View all button
        if (tracks.size > 3) {
            Surface(
                onClick = onViewAll,
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                ),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "All (${tracks.size})",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CompactTrackRow(
    track: TrackEntity?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    upFocusRequester: FocusRequester? = null,
) {
    val hasTrack = track != null

    Surface(
        onClick = onClick,
        enabled = hasTrack,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (upFocusRequester != null) {
                    Modifier.focusProperties { up = upFocusRequester }
                } else Modifier
            ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isCurrent && hasTrack)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (hasTrack) 0.3f else 0.1f),
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Label (Prev/Now/Next)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isCurrent && hasTrack)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (hasTrack) 1f else 0.4f),
                modifier = Modifier.width(36.dp),
                maxLines = 1
            )

            // Track title (or empty placeholder)
            Text(
                text = track?.title ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = if (!hasTrack)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                else if (isCurrent)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Timestamp
            track?.timestampSec?.let { timestampSec ->
                Text(
                    text = "• ${formatTimeFromSeconds(timestampSec)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TracklistDialog(
    tracks: List<TrackEntity>,
    currentTrack: TrackEntity?,
    onTrackClick: (TrackEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                .padding(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tracklist",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Surface(
                        onClick = onDismiss,
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Close",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Track list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(
                        items = tracks,
                        key = { _, track -> track.id }
                    ) { index, track ->
                        TrackListDialogItem(
                            track = track,
                            index = index + 1,
                            isCurrentTrack = track.id == currentTrack?.id,
                            onClick = { onTrackClick(track) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackListDialogItem(
    track: TrackEntity,
    index: Int,
    isCurrentTrack: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isCurrentTrack)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Track number
            Text(
                text = "%02d".format(index),
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrentTrack)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Current indicator
            if (isCurrentTrack) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            // Title
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrentTrack)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Timestamp
            track.timestampSec?.let { timestampSec ->
                Text(
                    text = formatTimeFromSeconds(timestampSec),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTimeFromSeconds(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}
