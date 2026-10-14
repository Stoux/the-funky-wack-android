package nl.stoux.tfw.feature.player.comments

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.stoux.tfw.core.common.network.dto.CommentDto
import nl.stoux.tfw.core.common.repository.CommentsRepository
import nl.stoux.tfw.core.common.repository.CommentsRepository.Sort
import nl.stoux.tfw.feature.player.PlayerViewModel
import nl.stoux.tfw.feature.player.util.formatRelativeIso
import nl.stoux.tfw.feature.player.util.formatTime

private sealed interface ComposerTarget {
    data class New(val timestampSeconds: Int) : ComposerTarget
    data class Reply(val parent: CommentDto) : ComposerTarget
    data class Edit(val comment: CommentDto) : ComposerTarget
}

/**
 * Comments for the currently playing liveset. Shown in the main content area
 * (like the queue) so the composer dialogs don't fight the player bottom sheet.
 */
@Composable
fun CommentsScreen(
    playerViewModel: PlayerViewModel,
    viewModel: CommentsViewModel,
    onClose: () -> Unit,
    onLoginRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val liveset by playerViewModel.currentLiveset.collectAsState()
    val positionMs by playerViewModel.positionMs.collectAsState()
    val openRequests by playerViewModel.commentsOpenRequests.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val context = LocalContext.current

    val livesetId = liveset?.liveset?.id
    val durationSeconds = liveset?.liveset?.durationSeconds

    // Fetch on every open and whenever the playing liveset changes
    LaunchedEffect(livesetId, openRequests) { viewModel.load(livesetId) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    var composer by remember { mutableStateOf<ComposerTarget?>(null) }
    var pendingDelete by remember { mutableStateOf<CommentDto?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        CommentsHeader(
            title = liveset?.liveset?.title,
            artist = liveset?.liveset?.artistName,
            sort = state.sort,
            onSortChange = viewModel::setSort,
            onRefresh = viewModel::refresh,
            onClose = onClose,
        )

        if (state.isLoading && state.comments.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                livesetId == null -> CenteredHint("Nothing playing")
                state.isLoading && state.comments.isEmpty() -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                state.error != null && state.comments.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = viewModel::refresh) { Text("Retry") }
                }
                state.comments.isEmpty() -> CenteredHint(
                    if (isLoggedIn) "No comments yet. Drop the first one!" else "No comments yet."
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(state.comments, key = { it.id }) { comment ->
                        CommentThread(
                            comment = comment,
                            canReply = isLoggedIn,
                            isAdmin = isAdmin,
                            onSeek = { sec -> playerViewModel.seekTo(sec * 1000L) },
                            onReply = { composer = ComposerTarget.Reply(comment) },
                            onEdit = { composer = ComposerTarget.Edit(it) },
                            onDelete = { pendingDelete = it },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }
        }

        // Footer: add at current position, or a login hint
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isLoggedIn) {
                    val currentSec = clampTimestamp((positionMs / 1000L).toInt(), durationSeconds)
                    Button(
                        onClick = { composer = ComposerTarget.New(currentSec) },
                        enabled = livesetId != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Comment at ${formatTime(currentSec * 1000L)}")
                    }
                } else {
                    Text(
                        "Log in to join the conversation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onLoginRequested) { Text("Log in") }
                }
            }
        }
    }

    composer?.let { target ->
        val (title, initial, confirm) = when (target) {
            is ComposerTarget.New -> Triple("Comment at ${formatTime(target.timestampSeconds * 1000L)}", "", "Post")
            is ComposerTarget.Reply -> Triple("Reply to ${target.parent.user?.name ?: "comment"}", "", "Reply")
            is ComposerTarget.Edit -> Triple("Edit comment", target.comment.body.orEmpty(), "Save")
        }
        ComposerDialog(
            title = title,
            initialText = initial,
            confirmLabel = confirm,
            isSubmitting = state.isSubmitting,
            onDismiss = { composer = null },
            onConfirm = { text ->
                val done = { composer = null }
                when (target) {
                    is ComposerTarget.New -> viewModel.post(text, target.timestampSeconds, onSuccess = done)
                    is ComposerTarget.Reply -> viewModel.post(
                        text,
                        target.parent.timestampSeconds,
                        parentId = target.parent.id,
                        onSuccess = done,
                    )
                    is ComposerTarget.Edit -> viewModel.edit(target.comment.id, text, onSuccess = done)
                }
            },
        )
    }

    pendingDelete?.let { comment ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete comment?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(comment.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CommentsHeader(
    title: String?,
    artist: String?,
    sort: Sort,
    onSortChange: (Sort) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Comments", style = MaterialTheme.typography.titleLarge)
            val subtitle = listOfNotNull(title, artist?.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = {
            onSortChange(if (sort == Sort.TIMESTAMP) Sort.DATE else Sort.TIMESTAMP)
        }) {
            Text(if (sort == Sort.TIMESTAMP) "By time" else "By date")
        }
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close comments") }
    }
}

@Composable
private fun CommentThread(
    comment: CommentDto,
    canReply: Boolean,
    isAdmin: Boolean,
    onSeek: (Int) -> Unit,
    onReply: () -> Unit,
    onEdit: (CommentDto) -> Unit,
    onDelete: (CommentDto) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            TimestampChip(seconds = comment.timestampSeconds, onClick = { onSeek(comment.timestampSeconds) })
            Spacer(Modifier.width(12.dp))
            CommentBody(
                comment = comment,
                // Replying to a deleted parent would silently become a top-level comment server-side
                onReply = if (canReply && !comment.isDeleted) onReply else null,
                isAdmin = isAdmin,
                onEdit = onEdit,
                onDelete = onDelete,
                modifier = Modifier.weight(1f),
            )
        }
        comment.replies.orEmpty().forEach { reply ->
            Row(modifier = Modifier.padding(start = 56.dp, top = 6.dp)) {
                CommentBody(
                    comment = reply,
                    // Replies are one level deep: replying to a reply threads under the top-level comment
                    onReply = if (canReply && !comment.isDeleted && !reply.isDeleted) onReply else null,
                    isAdmin = isAdmin,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TimestampChip(seconds: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Text(
            formatTime(seconds * 1000L),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CommentBody(
    comment: CommentDto,
    onReply: (() -> Unit)?,
    isAdmin: Boolean,
    onEdit: (CommentDto) -> Unit,
    onDelete: (CommentDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canEdit = comment.isOwn && !comment.isDeleted
    val canDelete = (comment.isOwn || isAdmin) && !comment.isDeleted

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val meta = buildString {
                formatRelativeIso(comment.createdAt)?.let { append(it) }
                if (!comment.isDeleted && comment.updatedAt != null && comment.updatedAt != comment.createdAt) {
                    if (isNotEmpty()) append(" · ")
                    append("edited")
                }
            }
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (comment.isDeleted) "[deleted]" else comment.user?.name ?: "Unknown",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (comment.isDeleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (meta.isNotBlank()) {
                    Text(
                        "  $meta",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (canEdit || canDelete) {
                CommentMenu(
                    onEdit = if (canEdit) ({ onEdit(comment) }) else null,
                    onDelete = if (canDelete) ({ onDelete(comment) }) else null,
                )
            }
        }

        if (comment.isDeleted || comment.body == null) {
            Text(
                "This comment was deleted",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(comment.body!!, style = MaterialTheme.typography.bodyMedium)
        }

        if (onReply != null) {
            Text(
                "Reply",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable(onClick = onReply)
                    .padding(vertical = 4.dp, horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun CommentMenu(onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Comment actions", modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            onEdit?.let {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { expanded = false; it() })
            }
            onDelete?.let {
                DropdownMenuItem(text = { Text("Delete") }, onClick = { expanded = false; it() })
            }
        }
    }
}

@Composable
private fun ComposerDialog(
    title: String,
    initialText: String,
    confirmLabel: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable(title) { mutableStateOf(initialText) }
    val max = CommentsRepository.MAX_BODY_LENGTH
    val valid = text.isNotBlank() && text.length <= max

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Say something about this moment") },
                minLines = 3,
                maxLines = 8,
                isError = text.length > max,
                supportingText = { Text("${text.length}/$max") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = valid && !isSubmitting) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
        },
    )
}

@Composable
private fun CenteredHint(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun clampTimestamp(seconds: Int, durationSeconds: Int?): Int {
    val upper = durationSeconds?.takeIf { it > 0 } ?: Int.MAX_VALUE
    return seconds.coerceIn(0, upper)
}
