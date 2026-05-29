package nl.stoux.tfw.core.common.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local mirror of `/api/playback/positions` so "continue listening" works
 * offline (e.g. start of a car trip before the network handshake completes).
 */
@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val livesetId: Long,
    val positionSeconds: Long,
    val updatedAt: String? = null,
)
