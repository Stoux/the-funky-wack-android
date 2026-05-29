package nl.stoux.tfw.core.common.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import nl.stoux.tfw.core.common.database.entity.PlaylistEntity
import nl.stoux.tfw.core.common.database.entity.PlaylistItemEntity

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY isOwn DESC, name")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE shareCode = :shareCode LIMIT 1")
    suspend fun findByShareCode(shareCode: String): PlaylistEntity?

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position")
    fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearItems(playlistId: Long)

    @Query("DELETE FROM playlists")
    suspend fun clearPlaylists()

    @Query("DELETE FROM playlist_items")
    suspend fun clearAllItems()

    @Transaction
    suspend fun replaceAll(playlists: List<PlaylistEntity>) {
        clearAllItems()
        clearPlaylists()
        playlists.forEach { upsertPlaylist(it) }
    }

    @Transaction
    suspend fun replaceItems(playlistId: Long, items: List<PlaylistItemEntity>) {
        clearItems(playlistId)
        upsertItems(items)
    }
}
