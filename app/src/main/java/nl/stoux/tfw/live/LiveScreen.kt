package nl.stoux.tfw.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import nl.stoux.tfw.core.common.network.dto.auth.LiveSessionDto

@Composable
fun LiveScreen(
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeRoom by viewModel.activeRoom.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    when {
        uiState.isLoading && uiState.sessions.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.error != null && uiState.sessions.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        uiState.error!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.refresh() }) {
                        Text("Retry")
                    }
                }
            }
        }
        uiState.sessions.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No active live sessions",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "When someone is playing a liveset, you'll be able to listen along here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { viewModel.refresh() }) {
                        Text("Refresh")
                    }
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.sessions, key = { it.channelToken }) { session ->
                    LiveSessionCard(
                        session = session,
                        isActive = activeRoom?.channelToken == session.channelToken,
                        isOwnSession = currentUser?.id != null && currentUser?.id == session.host.userId,
                        onJoinSync = { viewModel.joinSync(session.channelToken) },
                        onPlaySolo = { viewModel.playSolo(session.channelToken) },
                        onLeave = { viewModel.leave() },
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveSessionCard(
    session: LiveSessionDto,
    isActive: Boolean,
    isOwnSession: Boolean,
    onJoinSync: () -> Unit,
    onPlaySolo: () -> Unit,
    onLeave: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                session.liveset?.title ?: "Unknown liveset",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (isOwnSession) "You're hosting" else "Hosted by ${session.host.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isOwnSession) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (session.listenersCount > 0) {
                Text(
                    "${session.listenersCount} listener${if (session.listenersCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isOwnSession) {
                Spacer(modifier = Modifier.height(12.dp))
                if (isActive) {
                    Row {
                        Text(
                            "Listening along",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onLeave) { Text("Leave") }
                    }
                } else {
                    Row {
                        Button(onClick = onJoinSync, modifier = Modifier.weight(1f)) {
                            Text("Listen along")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(onClick = onPlaySolo) {
                            Text("Solo")
                        }
                    }
                }
            }
        }
    }
}
