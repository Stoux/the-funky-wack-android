package nl.stoux.tfw.core.common.repository

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.auth.PlaybackPositionDto
import nl.stoux.tfw.core.common.network.dto.auth.SavePositionRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs playback positions with the server for cross-device resume.
 */
@Singleton
class PlaybackPositionRepository @Inject constructor(
    private val api: ApiService,
    private val authRepository: AuthRepository,
) {
    private val _positions = MutableStateFlow<List<PlaybackPositionDto>>(emptyList())
    val positions: StateFlow<List<PlaybackPositionDto>> = _positions.asStateFlow()

    /**
     * Fetch all playback positions from server (all devices).
     */
    suspend fun refresh() {
        if (!authRepository.isLoggedIn.value) return
        try {
            _positions.value = api.getPlaybackPositions().positions
        } catch (e: Exception) {
            Log.w("PlaybackPositionRepo", "Failed to refresh positions", e)
        }
    }

    /**
     * Save a playback position to server.
     */
    suspend fun savePosition(livesetId: Long, positionSeconds: Long) {
        if (!authRepository.isLoggedIn.value) return
        try {
            api.savePlaybackPosition(livesetId, SavePositionRequest(positionSeconds))
        } catch (e: Exception) {
            Log.w("PlaybackPositionRepo", "Failed to save position", e)
        }
    }

    /**
     * Clear a playback position on the server (e.g., after finishing a liveset).
     */
    suspend fun clearPosition(livesetId: Long) {
        if (!authRepository.isLoggedIn.value) return
        try {
            api.clearPlaybackPosition(livesetId)
        } catch (e: Exception) {
            Log.w("PlaybackPositionRepo", "Failed to clear position", e)
        }
    }
}
