package nl.stoux.tfw.core.common.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Liveset table with a foreign key to Edition.
 * Stores descriptive fields and media-related metadata.
 */
@Entity(
    tableName = "livesets",
    indices = [Index("editionId"), Index("title"), Index("artistName")],
    foreignKeys = [
        ForeignKey(
            entity = EditionEntity::class,
            parentColumns = ["id"],
            childColumns = ["editionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LivesetEntity(
    @PrimaryKey val id: Long,
    val editionId: Long,
    val title: String,
    val artistName: String,
    val description: String? = null,
    val bpm: String? = null,
    val genre: String? = null,
    val durationSeconds: Int? = null,
    val startedAt: String? = null,
    val lineupOrder: Int? = null,
    /** stores null or a literal string; if API returns false, map to a sentinel like "INVALID" in mappers later */
    val timeslotRaw: String? = null,
    val soundcloudUrl: String? = null,
    val audioWaveformPath: String? = null,
    val audioWaveformUrl: String? = null,
    // Flattened audio qualities from previously separate AudioQualityEntity
    val lqUrl: String? = null,
    val hqUrl: String? = null,
    val losslessUrl: String? = null,
)