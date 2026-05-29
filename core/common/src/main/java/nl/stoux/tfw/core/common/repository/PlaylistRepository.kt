package nl.stoux.tfw.core.common.repository

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nl.stoux.tfw.core.common.database.dao.PlaylistDao
import nl.stoux.tfw.core.common.database.entity.PlaylistEntity
import nl.stoux.tfw.core.common.database.entity.PlaylistItemEntity
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.auth.AddPlaylistItemRequest
import nl.stoux.tfw.core.common.network.dto.auth.CreatePlaylistRequest
import nl.stoux.tfw.core.common.network.dto.auth.PlaylistDto
import nl.stoux.tfw.core.common.network.dto.auth.PlaylistItemDto
import nl.stoux.tfw.core.common.network.dto.auth.ReorderItem
import nl.stoux.tfw.core.common.network.dto.auth.ReorderPlaylistItemsRequest
import nl.stoux.tfw.core.common.network.dto.auth.UpdatePlaylistRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Full CRUD for playlists with stale-while-revalidate caching. The DAO is the
 * source of truth for the lobby list; getPlaylist still goes to the server
 * for fresh item ordering on demand.
 */
@Singleton
class PlaylistRepository @Inject constructor(
    private val api: ApiService,
    private val playlistDao: PlaylistDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val playlists: StateFlow<List<PlaylistDto>> = playlistDao.observeAll()
        .map { rows -> rows.map { it.toDto() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun refresh() {
        try {
            val response = api.getPlaylists()
            val combined = response.playlists + response.publicPlaylists
            playlistDao.replaceAll(combined.map { it.toEntity() })
        } catch (e: Exception) {
            Log.w("PlaylistRepository", "Failed to refresh playlists", e)
        }
    }

    suspend fun getPlaylist(shareCode: String): PlaylistDto {
        val playlist = api.getPlaylist(shareCode).playlist
        // Mirror items into the local cache when we can map them back to a playlistId
        playlist.items?.let { items ->
            playlistDao.replaceItems(
                playlistId = playlist.id,
                items = items.map { PlaylistItemEntity(playlistId = playlist.id, livesetId = it.livesetId, position = it.position) },
            )
        }
        playlistDao.upsertPlaylist(playlist.toEntity())
        return playlist
    }

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

private fun PlaylistDto.toEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    description = description,
    visibility = visibility,
    shareCode = shareCode,
    itemsCount = itemsCount,
    isOwn = isOwn,
    ownerName = user?.name,
)

private fun PlaylistEntity.toDto(): PlaylistDto = PlaylistDto(
    id = id,
    name = name,
    description = description,
    visibility = visibility,
    shareCode = shareCode,
    itemsCount = itemsCount,
    isOwn = isOwn,
    user = ownerName?.let { nl.stoux.tfw.core.common.network.dto.auth.PlaylistUserDto(id = -1, name = it) },
    items = null,
)
