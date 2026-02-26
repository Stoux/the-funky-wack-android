package nl.stoux.tfw.core.common.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import nl.stoux.tfw.core.common.database.entity.DownloadState
import nl.stoux.tfw.core.common.database.entity.LivesetDownloadEntity

@Dao
interface LivesetDownloadDao {

    /**
     * Get the download entry for a specific liveset (reactive).
     */
    @Query("SELECT * FROM liveset_downloads WHERE livesetId = :livesetId LIMIT 1")
    fun getByLivesetId(livesetId: Long): Flow<LivesetDownloadEntity?>

    /**
     * Get the download entry for a specific liveset (one-shot).
     */
    @Query("SELECT * FROM liveset_downloads WHERE livesetId = :livesetId LIMIT 1")
    suspend fun getByLivesetIdOnce(livesetId: Long): LivesetDownloadEntity?

    /**
     * Get all download entries (reactive).
     */
    @Query("SELECT * FROM liveset_downloads ORDER BY createdAt DESC")
    fun getAll(): Flow<List<LivesetDownloadEntity>>

    /**
     * Get all completed downloads (reactive).
     */
    @Query("SELECT * FROM liveset_downloads WHERE state = 'COMPLETED' ORDER BY completedAt DESC")
    fun getCompleted(): Flow<List<LivesetDownloadEntity>>

    /**
     * Get all completed downloads (one-shot).
     */
    @Query("SELECT * FROM liveset_downloads WHERE state = 'COMPLETED'")
    suspend fun getCompletedOnce(): List<LivesetDownloadEntity>

    /**
     * Get completed liveset IDs as a set (reactive).
     */
    @Query("SELECT livesetId FROM liveset_downloads WHERE state = 'COMPLETED'")
    fun getCompletedLivesetIds(): Flow<List<Long>>

    /**
     * Get downloads by state (reactive).
     */
    @Query("SELECT * FROM liveset_downloads WHERE state = :state ORDER BY createdAt ASC")
    fun getByState(state: DownloadState): Flow<List<LivesetDownloadEntity>>

    /**
     * Get download by Media3 download ID.
     */
    @Query("SELECT * FROM liveset_downloads WHERE media3DownloadId = :downloadId LIMIT 1")
    suspend fun getByMedia3DownloadId(downloadId: String): LivesetDownloadEntity?

    /**
     * Calculate total storage used by completed downloads.
     */
    @Query("SELECT SUM(audioFileSize) FROM liveset_downloads WHERE state = 'COMPLETED'")
    fun getTotalDownloadedBytes(): Flow<Long?>

    /**
     * Insert or update a download entry.
     */
    @Upsert
    suspend fun upsert(entity: LivesetDownloadEntity)

    /**
     * Insert a new download entry.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LivesetDownloadEntity): Long

    /**
     * Update an existing download entry.
     */
    @Update
    suspend fun update(entity: LivesetDownloadEntity)

    /**
     * Update download progress.
     */
    @Query("""
        UPDATE liveset_downloads
        SET progressPercent = :progress,
            bytesDownloaded = :bytesDownloaded,
            totalBytes = :totalBytes,
            state = :state
        WHERE media3DownloadId = :downloadId
    """)
    suspend fun updateProgress(
        downloadId: String,
        progress: Float,
        bytesDownloaded: Long,
        totalBytes: Long,
        state: DownloadState = DownloadState.DOWNLOADING,
    )

    /**
     * Mark a download as completed.
     */
    @Query("""
        UPDATE liveset_downloads
        SET state = 'COMPLETED',
            progressPercent = 100.0,
            completedAt = :completedAt,
            audioFilePath = :audioFilePath,
            audioFileSize = :audioFileSize,
            waveformJson = :waveformJson
        WHERE media3DownloadId = :downloadId
    """)
    suspend fun markCompleted(
        downloadId: String,
        completedAt: Long,
        audioFilePath: String,
        audioFileSize: Long,
        waveformJson: String?,
    )

    /**
     * Mark a download as failed.
     */
    @Query("""
        UPDATE liveset_downloads
        SET state = 'FAILED',
            failureReason = :reason
        WHERE media3DownloadId = :downloadId
    """)
    suspend fun markFailed(downloadId: String, reason: String?)

    /**
     * Delete a download entry by liveset ID.
     */
    @Query("DELETE FROM liveset_downloads WHERE livesetId = :livesetId")
    suspend fun deleteByLivesetId(livesetId: Long)

    /**
     * Delete a download entry by Media3 download ID.
     */
    @Query("DELETE FROM liveset_downloads WHERE media3DownloadId = :downloadId")
    suspend fun deleteByMedia3DownloadId(downloadId: String)

    /**
     * Delete all download entries.
     */
    @Query("DELETE FROM liveset_downloads")
    suspend fun deleteAll()
}
