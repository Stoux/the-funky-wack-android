package nl.stoux.tfw.tv.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.Reply
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.stoux.tfw.core.common.network.dto.AppNotificationDto
import nl.stoux.tfw.feature.player.util.formatRelativeIso
import nl.stoux.tfw.feature.player.util.formatTime
import nl.stoux.tfw.tv.ui.common.requestFocusWhenAttached

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvNotificationsScreen(
    onOpenPlayer: () -> Unit,
    onBack: () -> Unit,
    viewModel: TvNotificationsViewModel = hiltViewModel(),
) {
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    // Login-only screen; leave if the session goes away
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) onBack()
    }

    val listState = rememberLazyListState()
    val itemFocusRequester = remember { FocusRequester() }
    val refreshFocusRequester = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    // Survives the trip to NowPlaying, so back lands on the notification that was opened
    var lastOpenedId by rememberSaveable { mutableStateOf<String?>(null) }
    val focusTargetIndex = notifications.indexOfFirst { it.id == lastOpenedId }.coerceAtLeast(0)

    // Initial focus: last opened (else first) notification, or Refresh while the list is empty.
    // Once a notification got focus we leave it alone (refreshes keep keys stable).
    LaunchedEffect(notifications.isNotEmpty()) {
        if (initialFocusDone) return@LaunchedEffect
        if (notifications.isNotEmpty()) {
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == focusTargetIndex }) {
                listState.scrollToItem(focusTargetIndex)
            }
            initialFocusDone = requestFocusWhenAttached(itemFocusRequester)
        } else {
            requestFocusWhenAttached(refreshFocusRequester)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = when {
                        isLoading && notifications.isEmpty() -> "Loading..."
                        unreadCount > 0 -> "$unreadCount unread"
                        else -> "All caught up"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
            if (unreadCount > 0) {
                HeaderButton(text = "Mark all read", onClick = viewModel::markAllRead)
            }
            HeaderButton(
                text = if (isLoading) "Refreshing..." else "Refresh",
                onClick = viewModel::refresh,
                modifier = Modifier.focusRequester(refreshFocusRequester),
            )
            HeaderButton(text = "Back", onClick = onBack)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (notifications.isEmpty()) {
                Text(
                    text = if (isLoading) "Loading notifications..."
                    else "No notifications yet.\nReplies to your comments and new comments on sets you commented on show up here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(notifications, key = { _, it -> it.id }) { index, notification ->
                        NotificationItem(
                            notification = notification,
                            onClick = {
                                lastOpenedId = notification.id
                                if (viewModel.open(notification)) onOpenPlayer()
                            },
                            modifier = if (index == focusTargetIndex) Modifier.focusRequester(itemFocusRequester) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NotificationItem(
    notification: AppNotificationDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = notification.data
    val isReply = (notification.type ?: data.type) == AppNotificationDto.TYPE_COMMENT_REPLY
    val unread = !notification.isRead
    val livesetTitle = data.livesetTitle ?: "a liveset"
    val headline = if (isReply) {
        "${data.replierName ?: "Someone"} replied to your comment"
    } else {
        "${data.commenterName ?: "Someone"} commented on $livesetTitle"
    }
    val meta = buildString {
        if (isReply) append(livesetTitle)
        data.timestampSeconds?.let {
            if (isNotEmpty()) append(" @ ") else append("@ ")
            append(formatTime(it * 1000L))
        }
        formatRelativeIso(notification.createdAt)?.let {
            if (isNotEmpty()) append(" · ")
            append(it)
        }
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (unread) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (isReply) Icons.AutoMirrored.Outlined.Reply else Icons.AutoMirrored.Outlined.Comment,
                contentDescription = null,
                tint = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp).size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                data.bodyPreview?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "\"$it\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (unread) {
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeaderButton(
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
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
