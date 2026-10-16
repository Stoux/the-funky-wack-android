package nl.stoux.tfw.tv.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.stoux.tfw.core.common.network.dto.CommentDto
import nl.stoux.tfw.feature.player.util.formatRelativeIso
import nl.stoux.tfw.feature.player.util.formatTime
import nl.stoux.tfw.tv.ui.common.requestFocusWhenAttached

/**
 * Read-only comments side panel over the now playing screen. Back (or Close) dismisses,
 * selecting a comment seeks to its timestamp. Initial focus lands on the comment at or
 * just before [currentPositionMs].
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvCommentsPanel(
    livesetTitle: String,
    commentsCount: Int?,
    state: TvCommentsUiState,
    currentPositionMs: Long,
    onCommentClick: (CommentDto) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onInteraction: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                // Keys go to the dialog window, keep the now playing inactivity timer (OLED mode) at bay
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) onInteraction()
                    false
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.48f)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 32.dp, vertical = 28.dp),
            ) {
                PanelContent(
                    livesetTitle = livesetTitle,
                    commentsCount = commentsCount,
                    state = state,
                    currentPositionMs = currentPositionMs,
                    onCommentClick = onCommentClick,
                    onRetry = onRetry,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ColumnScope.PanelContent(
    livesetTitle: String,
    commentsCount: Int?,
    state: TvCommentsUiState,
    currentPositionMs: Long,
    onCommentClick: (CommentDto) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = state.rows
    val listState = rememberLazyListState()
    val closeFocusRequester = remember { FocusRequester() }
    val retryFocusRequester = remember { FocusRequester() }
    val targetFocusRequester = remember { FocusRequester() }
    var focusTargetIndex by remember { mutableStateOf<Int?>(null) }
    var initialFocusDone by remember { mutableStateOf(false) }

    // Initial focus: the comment nearest the playhead once loaded, Close (or Retry) until then.
    // Only once per open, so live refetches don't yank focus around.
    LaunchedEffect(rows.isNotEmpty(), state.error != null) {
        if (initialFocusDone) return@LaunchedEffect
        if (rows.isNotEmpty()) {
            val positionSec = currentPositionMs / 1000
            val index = rows.indexOfLast { !it.isReply && it.comment.timestampSeconds <= positionSec }
                .coerceAtLeast(0)
            focusTargetIndex = index
            listState.scrollToItem(index)
            initialFocusDone = requestFocusWhenAttached(targetFocusRequester)
        } else if (state.error != null) {
            requestFocusWhenAttached(retryFocusRequester)
        } else {
            requestFocusWhenAttached(closeFocusRequester)
        }
    }

    // Header
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (commentsCount != null && commentsCount > 0) "Comments ($commentsCount)" else "Comments",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = livesetTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PanelButton(
            text = "Close",
            onClick = onDismiss,
            modifier = Modifier.focusRequester(closeFocusRequester),
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when {
            rows.isNotEmpty() -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(rows, key = { _, row -> row.comment.id }) { index, row ->
                    CommentItem(
                        row = row,
                        onClick = { onCommentClick(row.comment) },
                        modifier = if (index == focusTargetIndex) {
                            Modifier.focusRequester(targetFocusRequester)
                        } else Modifier,
                    )
                }
            }

            state.error != null -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PanelButton(
                    text = "Retry",
                    onClick = onRetry,
                    modifier = Modifier.focusRequester(retryFocusRequester),
                )
            }

            state.isLoading || !state.hasLoaded -> Text(
                text = "Loading comments...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )

            else -> Text(
                text = "No comments yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Select a comment to jump to that moment. Comment from the phone app or the website.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CommentItem(
    row: TvCommentRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val comment = row.comment
    val deleted = comment.isDeleted || comment.body == null

    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        if (row.isReply) {
            // Indent + thread line for replies
            Spacer(modifier = Modifier.width(20.dp))
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        Surface(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (row.isReply) 0.25f else 0.45f),
                focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = formatTime(comment.timestampSeconds * 1000L),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (deleted) "[deleted]" else comment.user?.name ?: "Unknown",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (deleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!deleted) {
                        formatRelativeIso(comment.createdAt)?.let { relative ->
                            Text(
                                text = relative,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (deleted) {
                    Text(
                        text = "This comment was deleted",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = comment.body.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PanelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
