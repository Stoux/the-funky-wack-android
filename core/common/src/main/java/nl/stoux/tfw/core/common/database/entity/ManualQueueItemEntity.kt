package nl.stoux.tfw.core.common.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Manual queue item that references a Liveset only.
 * All metadata (title/artist/duration/artwork) is already available in the DB via Liveset/Edition tables.
 */
@Entity(tableName = "manual_queue_item")
data class ManualQueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val livesetId: Long,
    val orderIndex: Double,
    val addedAtMs: Long,
)
