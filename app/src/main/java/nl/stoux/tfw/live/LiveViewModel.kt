package nl.stoux.tfw.live

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.auth.LiveSessionDto
import nl.stoux.tfw.service.playback.live.ListenAlongCoordinator
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import javax.inject.Inject

data class LiveUiState(
    val sessions: List<LiveSessionDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val api: ApiService,
    private val listenAlongCoordinator: ListenAlongCoordinator,
    private val queueManager: QueueManager,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveUiState())
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()

    val activeRoom: StateFlow<ListenAlongCoordinator.ActiveRoom?> = listenAlongCoordinator.activeRoom
    val currentUser = authRepository.currentUser

    init {
        refresh()
        listenAlongCoordinator.subscribePresenceLive()
        viewModelScope.launch {
            listenAlongCoordinator.sessionsUpdated.collect { refresh() }
        }
    }

    override fun onCleared() {
        listenAlongCoordinator.unsubscribePresenceLive()
        super.onCleared()
    }

    fun joinSync(channelToken: String) {
        viewModelScope.launch {
            listenAlongCoordinator.join(channelToken, mode = "synced")
                .onFailure { Log.w("LiveViewModel", "Failed to join $channelToken", it) }
        }
    }

    /**
     * Start playing the same liveset as the host, locally — no server-side
     * join, no listener count bump, no Reverb subscription. Just snapshots
     * the host's current track + position and queues it on our own player.
     */
    fun playSolo(channelToken: String) {
        viewModelScope.launch {
            val state = runCatching { api.getLiveRoomState(channelToken) }
                .onFailure { Log.w("LiveViewModel", "Solo state fetch failed", it) }
                .getOrNull() ?: return@launch
            val livesetId = state.liveset?.id ?: return@launch
            queueManager.setContextFromLiveset(
                livesetId = livesetId,
                startPositionMs = state.position * 1000L,
                autoplay = true,
            )
        }
    }

    fun leave() {
        viewModelScope.launch { listenAlongCoordinator.leave() }
    }

    fun pauseSync() {
        viewModelScope.launch { listenAlongCoordinator.pauseSync() }
    }

    fun resumeSync() {
        viewModelScope.launch {
            listenAlongCoordinator.resumeSync()
            // Host may have moved tracks while we were paused — pull fresh state
            listenAlongCoordinator.fetchState()
        }
    }

    fun detach() {
        viewModelScope.launch { listenAlongCoordinator.detach() }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                Log.d("LiveViewModel", "Fetching live sessions...")
                val sessions = api.getLiveSessions().sessions
                Log.d("LiveViewModel", "Got ${sessions.size} live sessions: ${sessions.map { "${it.channelToken}: ${it.host.name} playing ${it.liveset?.title ?: "?"} (${it.listenersCount} listeners)" }}")
                _uiState.value = _uiState.value.copy(
                    sessions = sessions,
                    isLoading = false,
                )
                listenAlongCoordinator.setSessionCount(sessions.size)
            } catch (e: Exception) {
                Log.e("LiveViewModel", "Failed to fetch live sessions", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load live sessions",
                )
            }
        }
    }
}
