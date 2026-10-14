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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.stoux.tfw.core.common.repository.EditionRepository
import nl.stoux.tfw.service.playback.service.queue.QueueItem
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Queue screen:
 * - Tap an item to play it now, swipe left to remove.
 * - Upcoming manually queued items (★) can be reordered with the drag handle.
 * - "Clear" removes all upcoming manually queued items.
 */
@Composable
fun QueueScreen(
    queueManager: QueueManager,
    editionRepository: EditionRepository,
    modifier: Modifier = Modifier,
) {
    val state by queueManager.state.collectAsState()
    val currentInstanceId = state.effective.getOrNull(state.currentEffectiveIndex)?.instanceId

    // Local copy so drags can move rows live; resets to the real queue on every change
    var items by remember(state.effective) { mutableStateOf(state.effective) }
    fun isMovable(item: QueueItem): Boolean {
        val index = items.indexOf(item)
        val currentIndex = items.indexOfFirst { it.instanceId == currentInstanceId }
        return item.manualEntryId != null && index > currentIndex
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val target = items.getOrNull(to.index) ?: return@rememberReorderableLazyListState
        if (!isMovable(target)) return@rememberReorderableLazyListState
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    val hasUpcomingManual = items.any { isMovable(it) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Queue",
                style = MaterialTheme.typography.headlineSmall,
            )
            if (hasUpcomingManual) {
                TextButton(onClick = { queueManager.clearManual() }) {
                    Text("Clear")
                }
            }
        }
        Divider()
        LazyColumn(state = lazyListState) {
            // No lane headers; items are interleaved. Just show effective order.
            items(
                items = items,
                key = { item -> item.instanceId }
            ) { item ->
                val isCurrent = item.instanceId == currentInstanceId
                val isManual = item.manualEntryId != null
                val movable = isMovable(item)
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

                ReorderableItem(reorderableState, key = item.instanceId) {
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
                                trailingContent = if (movable) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.DragHandle,
                                            contentDescription = "Reorder",
                                            modifier = Modifier.draggableHandle(
                                                onDragStopped = {
                                                    // Position within the manual lane = manual items above it
                                                    val position = items.indexOfFirst { it.instanceId == item.instanceId }
                                                    val manualIndex = items.take(position).count { it.manualEntryId != null }
                                                    queueManager.moveManual(item.instanceId, manualIndex)
                                                }
                                            ),
                                        )
                                    }
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val index = state.effective.indexOfFirst { it.instanceId == item.instanceId }
                                        if (index >= 0) queueManager.skipToEffective(index)
                                    }
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    )
                }
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
