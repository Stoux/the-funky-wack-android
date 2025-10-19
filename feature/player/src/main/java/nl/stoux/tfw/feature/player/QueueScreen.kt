package nl.stoux.tfw.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.stoux.tfw.core.common.repository.EditionRepository
import nl.stoux.tfw.service.playback.service.queue.QueueManager

/**
 * Queue screen with improved visuals and controls:
 * - Tap an item to play it now.
 * - Section headers for Manual and Up next (no per-row lane labels).
 * - Header includes a repeat-mode toggle (Off → One → Context).
 */
@Composable
fun QueueScreen(
    queueManager: QueueManager,
    editionRepository: EditionRepository,
    modifier: Modifier = Modifier,
) {
    val state by queueManager.state.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Queue",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Divider()
        // Effective queue listing with headers and nicer spacing
        LazyColumn {
            // No lane headers; items are interleaved. Just show effective order.
            itemsIndexed(
                items = state.effective,
                key = { _, item -> item.instanceId }
            ) { index, item ->
                val isCurrent = index == state.currentEffectiveIndex
                val isManual = item.manualEntryId != null
                val containerColor = if (isCurrent) ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) else ListItemDefaults.colors()

                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            // Remove by stable instance id to avoid index mismatches
                            queueManager.removeByInstanceId(item.instanceId)
                            true
                        } else {
                            false
                        }
                    }
                )

                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = true,
                    backgroundContent = {
                        // Trash icon aligned to the right so it becomes visible early when swiping left
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 20.dp)
                            )
                        }
                    },
                    content = {
                        ListItem(
                            colors = containerColor,
                            headlineContent = {
                                LivesetRowTexts(
                                    editionRepository = editionRepository,
                                    livesetId = item.livesetId,
                                    isCurrent = isCurrent,
                                    isManual = isManual,
                                )
                            },
                            // Hide reorder button for now: no trailingContent
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { queueManager.skipToEffective(index) }
                                .padding(horizontal = 4.dp)
                        )
                    }
                )
                Divider()
            }
        }
    }
}


@Composable
private fun SectionHeader(text: String) {
    ListItem(
        headlineContent = { Text(text, style = MaterialTheme.typography.labelLarge) },
        modifier = Modifier.padding(top = 8.dp)
    )
    Divider()
}

@Composable
private fun LivesetRowTexts(
    editionRepository: EditionRepository,
    livesetId: Long,
    isCurrent: Boolean,
    isManual: Boolean,
) {
    val details by remember(livesetId) { editionRepository.findLiveset(livesetId) }.collectAsState(initial = null)
    val title = details?.liveset?.title ?: "Loading…"
    val editionNo = details?.edition?.number
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isManual) {
                Text(
                    text = "★",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (!editionNo.isNullOrBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TFW #$editionNo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (isCurrent) {
                    Text(
                        text = "Now playing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        } else if (isCurrent) {
            // If no edition number available, still show now playing indicator
            Text(
                text = "Now playing",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}
