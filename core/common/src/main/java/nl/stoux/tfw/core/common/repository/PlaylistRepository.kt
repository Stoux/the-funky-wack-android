package nl.stoux.tfw.core.common.repository

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.auth.AddPlaylistItemRequest
import nl.stoux.tfw.core.common.network.dto.auth.CreatePlaylistRequest
import nl.stoux.tfw.core.common.network.dto.auth.PlaylistDto
import nl.stoux.tfw.core.common.network.dto.auth.ReorderItem
import nl.stoux.tfw.core.common.network.dto.auth.ReorderPlaylistItemsRequest
import nl.stoux.tfw.core.common.network.dto.auth.UpdatePlaylistRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Full CRUD for playlists, server-only (no Room caching).
 */
@Singleton
class PlaylistRepository @Inject constructor(
    private val api: ApiService,
) {
    private val _playlists = MutableStateFlow<List<PlaylistDto>>(emptyList())
    val playlists: StateFlow<List<PlaylistDto>> = _playlists.asStateFlow()

    suspend fun refresh() {
        try {
            val response = api.getPlaylists()
            _playlists.value = response.playlists + response.publicPlaylists
        } catch (e: Exception) {
            Log.w("PlaylistRepository", "Failed to refresh playlists", e)
        }
    }

    suspend fun getPlaylist(shareCode: String): PlaylistDto = api.getPlaylist(shareCode).playlist

    suspend fun createPlaylist(name: String, description: String? = null, visibility: String = "private"): PlaylistDto {
        val playlist = api.createPlaylist(CreatePlaylistRequest(name, description, visibility)).playlist
        refresh()
        return playlist
    }

    suspend fun updatePlaylist(shareCode: String, name: String? = null, description: String? = null, visibility: String? = null): PlaylistDto {
        val playlist = api.updatePlaylist(shareCode, UpdatePlaylistRequest(name, description, visibility)).playlist
        refresh()
        return playlist
    }

    suspend fun deletePlaylist(shareCode: String) {
        api.deletePlaylist(shareCode)
        refresh()
    }

    suspend fun addItem(shareCode: String, livesetId: Long) {
        api.addPlaylistItem(shareCode, AddPlaylistItemRequest(livesetId))
    }

    suspend fun removeItem(shareCode: String, livesetId: Long) {
        api.removePlaylistItem(shareCode, livesetId)
    }

    suspend fun reorderItems(shareCode: String, items: List<ReorderItem>) {
        api.reorderPlaylistItems(shareCode, ReorderPlaylistItemsRequest(items))
    }
}
