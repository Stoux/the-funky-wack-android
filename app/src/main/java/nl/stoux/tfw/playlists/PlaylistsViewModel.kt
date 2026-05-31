package nl.stoux.tfw.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.network.dto.auth.PlaylistDto
import nl.stoux.tfw.core.common.repository.EditionRepository
import nl.stoux.tfw.core.common.repository.PlaylistRepository
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import javax.inject.Inject

data class PlaylistsUiState(
    val isLoading: Boolean = false,
    val selectedPlaylist: PlaylistDto? = null,
    val error: String? = null,
)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val queueManager: QueueManager,
    private val editionRepository: EditionRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    /** Look up a cached liveset by id (used by the detail row to resolve items). */
    fun resolveLiveset(livesetId: Long): LivesetWithDetails? =
        editionRepository.getCachedLiveset(livesetId)

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn
    val playlists: StateFlow<List<PlaylistDto>> = playlistRepository.playlists

    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { playlistRepository.refresh() }
    }

    fun createPlaylist(name: String, description: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                playlistRepository.createPlaylist(name, description)
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun deletePlaylist(shareCode: String) {
        viewModelScope.launch {
            runCatching { playlistRepository.deletePlaylist(shareCode) }
        }
    }

    fun selectPlaylist(playlist: PlaylistDto) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val detail = playlistRepository.getPlaylist(playlist.shareCode)
                _uiState.value = _uiState.value.copy(selectedPlaylist = detail, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedPlaylist = null)
    }

    /**
     * Play a liveset from within a playlist context — the playlist's items
     * (in order) become the queue, starting at this liveset.
     */
    fun playFromPlaylist(shareCode: String, livesetId: Long) {
        queueManager.setContextFromPlaylist(
            shareCode = shareCode,
            startLivesetId = livesetId,
            startPositionMs = 0L,
            autoplay = true,
        )
    }

    fun removeFromPlaylist(shareCode: String, livesetId: Long) {
        viewModelScope.launch {
            runCatching { playlistRepository.removeItem(shareCode, livesetId) }
            // Refetch detail so the items list reflects the removal
            val current = _uiState.value.selectedPlaylist ?: return@launch
            if (current.shareCode != shareCode) return@launch
            runCatching {
                val detail = playlistRepository.getPlaylist(shareCode)
                _uiState.value = _uiState.value.copy(selectedPlaylist = detail)
            }
        }
    }
}
