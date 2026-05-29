package nl.stoux.tfw.core.common.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val description: String? = null,
    val visibility: String = "private",
    val shareCode: String,
    val itemsCount: Int = 0,
    val isOwn: Boolean = false,
    val ownerName: String? = null,
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "livesetId"],
    indices = [Index("playlistId"), Index("livesetId")],
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val livesetId: Long,
    val position: Int = 0,
)
