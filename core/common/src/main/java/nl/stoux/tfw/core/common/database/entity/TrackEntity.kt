package nl.stoux.tfw.core.common.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Track table for timestamped tracklist entries within a liveset.
 */
@Entity(
    tableName = "tracks",
    indices = [Index("livesetId"), Index("orderInSet")],
    foreignKeys = [
        ForeignKey(
            entity = LivesetEntity::class,
            parentColumns = ["id"],
            childColumns = ["livesetId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackEntity(
    @PrimaryKey val id: Long,
    val livesetId: Long,
    val title: String,
    /** Timestamp in seconds from start of liveset */
    val timestampSec: Int? = null,
    val orderInSet: Int,
)