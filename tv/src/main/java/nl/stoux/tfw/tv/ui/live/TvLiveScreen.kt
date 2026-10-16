package nl.stoux.tfw.tv.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.stoux.tfw.tv.ui.common.TvEmptyState
import nl.stoux.tfw.tv.ui.common.TvListCard
import nl.stoux.tfw.tv.ui.common.TvScreenScaffold
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

    TvScreenScaffold(
        title = "Live",
        subtitle = "Listen along with someone who's playing right now",
        onBack = onBack,
    ) {
        when {
            state.isLoading && state.sessions.isEmpty() -> TvEmptyState("Loading…")
            state.sessions.isEmpty() -> TvEmptyState("Nobody is listening right now")
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.sessions, key = { it.channelToken }) { session ->
                    val isActive = activeRoom?.channelToken == session.channelToken
                    // Select toggles: join in sync, or leave when already listening along
                    TvListCard(onClick = {
                        if (isActive) viewModel.leave() else viewModel.joinSync(session.channelToken)
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = session.liveset?.title ?: "Unknown liveset",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (isActive) "Listening along · select to leave" else "Select to listen along",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val listeners = session.listenersCount
                        Text(
                            text = buildString {
                                append("Hosted by ${session.host.name}")
                                if (listeners > 0) append(" · $listeners listener${if (listeners != 1) "s" else ""}")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
