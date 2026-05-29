package nl.stoux.tfw.tv.ui.live

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.auth.LiveSessionDto
import nl.stoux.tfw.service.playback.live.ListenAlongCoordinator
import javax.inject.Inject

data class TvLiveUiState(
    val sessions: List<LiveSessionDto> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class TvLiveViewModel @Inject constructor(
    private val api: ApiService,
    private val listenAlongCoordinator: ListenAlongCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvLiveUiState(isLoading = true))
    val uiState: StateFlow<TvLiveUiState> = _uiState.asStateFlow()

    val activeRoom: StateFlow<ListenAlongCoordinator.ActiveRoom?> = listenAlongCoordinator.activeRoom

    init {
        refresh()
        listenAlongCoordinator.subscribePresenceLive()
        viewModelScope.launch {
            listenAlongCoordinator.sessionsUpdated.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val sessions = runCatching { api.getLiveSessions().sessions }.getOrDefault(emptyList())
            _uiState.value = TvLiveUiState(sessions = sessions, isLoading = false)
            listenAlongCoordinator.setSessionCount(sessions.size)
        }
    }

    fun joinSync(channelToken: String) {
        viewModelScope.launch {
            listenAlongCoordinator.join(channelToken, mode = "synced")
        }
    }

    fun leave() {
        viewModelScope.launch { listenAlongCoordinator.leave() }
    }

    override fun onCleared() {
        listenAlongCoordinator.unsubscribePresenceLive()
        super.onCleared()
    }
}

@Composable
fun TvLiveScreen(
    onBack: () -> Unit,
    viewModel: TvLiveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val activeRoom by viewModel.activeRoom.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        Text("Live", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(24.dp))
        when {
            state.isLoading && state.sessions.isEmpty() -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.sessions.isEmpty() -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No active live sessions",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.sessions, key = { it.channelToken }) { session ->
                    val isActive = activeRoom?.channelToken == session.channelToken
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                session.liveset?.title ?: "Unknown liveset",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "Hosted by ${session.host.name}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (session.listenersCount > 0) {
                                Text(
                                    "${session.listenersCount} listener${if (session.listenersCount != 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            if (isActive) {
                                Button(onClick = { viewModel.leave() }) { Text("Leave") }
                            } else {
                                Button(onClick = { viewModel.joinSync(session.channelToken) }) {
                                    Text("Listen along")
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}
