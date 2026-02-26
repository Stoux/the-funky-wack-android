package nl.stoux.tfw.core.common.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * State of a liveset download.
 */
enum class DownloadState {
    /** Queued for download but not yet started */
    QUEUED,
    /** Currently downloading */
    DOWNLOADING,
    /** Download completed successfully */
    COMPLETED,
    /** Download failed */
    FAILED,
    /** Being removed/deleted */
    REMOVING,
}

/**
 * Tracks downloaded livesets for offline playback.
 * Each liveset can have at most one download entry (one quality at a time).
 */
@Entity(
    tableName = "liveset_downloads",
    indices = [Index("livesetId", unique = true), Index("state")],
    foreignKeys = [
        ForeignKey(
            entity = LivesetEntity::class,
            parentColumns = ["id"],
            childColumns = ["livesetId"],
            // NO_ACTION: Don't delete downloads when liveset data is refreshed
            // Downloads should persist independently of the catalog cache
            onDelete = ForeignKey.NO_ACTION,
            deferred = true,
        )
    ]
)
data class LivesetDownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Foreign key to the liveset being downloaded */
    val livesetId: Long,

    /** Current state of the download */
    val state: DownloadState,

    /** Download progress as percentage (0-100) */
    val progressPercent: Float = 0f,

    /** Bytes downloaded so far */
    val bytesDownloaded: Long = 0,

    /** Total expected bytes (may be 0 if unknown) */
    val totalBytes: Long = 0,

    /** Quality of the downloaded audio: "low", "high", or "lossless" */
    val quality: String,

    /** Local file path for the downloaded audio file */
    val audioFilePath: String? = null,

    /** Size of the downloaded audio file in bytes */
    val audioFileSize: Long = 0,

    /** Waveform JSON data stored inline (avoids separate file management) */
    val waveformJson: String? = null,

    /** Media3 DownloadManager's download ID for tracking */
    val media3DownloadId: String,

    /** Timestamp when download was initiated */
    val createdAt: Long,

    /** Timestamp when download completed (null if not completed) */
    val completedAt: Long? = null,

    /** Human-readable failure reason if state is FAILED */
    val failureReason: String? = null,
)
