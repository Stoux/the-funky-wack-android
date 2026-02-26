package nl.stoux.tfw.service.playback.download

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.stoux.tfw.core.common.database.dao.LivesetDownloadDao
import nl.stoux.tfw.core.common.database.entity.DownloadState
import nl.stoux.tfw.core.common.database.entity.LivesetDownloadEntity
import nl.stoux.tfw.core.common.repository.EditionRepository
import nl.stoux.tfw.core.common.repository.LivesetCleanupCallback
import nl.stoux.tfw.core.common.repository.LivesetCleanupCallbackHolder
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository.AudioQuality
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Status of a download for UI display.
 */
sealed class DownloadStatus {
    /** No download exists for this liveset */
    data object NotDownloaded : DownloadStatus()

    /** Download is queued or in progress */
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : DownloadStatus()

    /** Download completed successfully */
    data class Completed(
        val quality: String,
        val fileSizeBytes: Long,
    ) : DownloadStatus()

    /** Download failed */
    data class Failed(val reason: String?) : DownloadStatus()
}

/**
 * Result of starting a download.
 */
sealed class DownloadResult {
    data object Success : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}

/**
 * High-level repository for managing liveset downloads.
 * Abstracts Media3 DownloadManager and provides a clean API for the UI layer.
 */
@UnstableApi
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val downloadDao: LivesetDownloadDao,
    private val downloadTracker: DownloadTracker,
    private val editionRepository: EditionRepository,
    @DownloadCache private val cache: Cache,
    cleanupCallbackHolder: LivesetCleanupCallbackHolder,
) : LivesetCleanupCallback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Register this repository as the cleanup callback
        cleanupCallbackHolder.callback = this
    }

    companion object {
        private const val TAG = "DownloadRepository"
    }

    /**
     * Start downloading a liveset with the specified quality.
     * Downloads both the audio file and waveform data.
     */
    suspend fun startDownload(livesetId: Long, quality: AudioQuality): DownloadResult {
        return withContext(Dispatchers.IO) {
            try {
                // Get liveset details
                val lwd = editionRepository.findLiveset(livesetId).first()
                    ?: return@withContext DownloadResult.Error("Liveset not found")

                val liveset = lwd.liveset

                // Get the URL for the requested quality
                val audioUrl = when (quality) {
                    AudioQuality.LOW -> liveset.lqUrl
                    AudioQuality.HIGH -> liveset.hqUrl
                    AudioQuality.LOSSLESS -> liveset.losslessUrl
                } ?: return@withContext DownloadResult.Error("Quality not available for this liveset")

                // Check if already downloading or downloaded
                val existing = downloadDao.getByLivesetIdOnce(livesetId)
                if (existing != null) {
                    when (existing.state) {
                        DownloadState.QUEUED, DownloadState.DOWNLOADING -> {
                            return@withContext DownloadResult.Error("Download already in progress")
                        }
                        DownloadState.COMPLETED -> {
                            return@withContext DownloadResult.Error("Already downloaded")
                        }
                        else -> {
                            // Remove failed/removing entry and start fresh
                            downloadDao.deleteByLivesetId(livesetId)
                        }
                    }
                }

                // Generate a unique download ID
                val downloadId = "liveset_${livesetId}_${UUID.randomUUID()}"

                // Fetch waveform data before starting audio download
                val waveformJson = fetchWaveformJson(liveset.audioWaveformUrl)

                // Create database entry
                val entity = LivesetDownloadEntity(
                    livesetId = livesetId,
                    state = DownloadState.QUEUED,
                    quality = quality.key,
                    media3DownloadId = downloadId,
                    createdAt = System.currentTimeMillis(),
                    waveformJson = waveformJson,
                )
                downloadDao.insert(entity)

                // Create Media3 download request
                val downloadRequest = DownloadRequest.Builder(downloadId, audioUrl.toUri())
                    .setData(livesetId.toString().toByteArray()) // Store liveset ID for reference
                    .build()

                // Start the download via service
                TfwDownloadService.sendAddDownload(context, downloadRequest, /* foreground */ true)

                Log.d(TAG, "Started download: $downloadId for liveset $livesetId")
                DownloadResult.Success

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start download", e)
                DownloadResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Cancel an in-progress download.
     */
    suspend fun cancelDownload(livesetId: Long) {
        withContext(Dispatchers.IO) {
            val entity = downloadDao.getByLivesetIdOnce(livesetId) ?: return@withContext
            TfwDownloadService.sendRemoveDownload(context, entity.media3DownloadId, /* foreground */ false)
            // Database entry will be removed by DownloadTracker.onDownloadRemoved
        }
    }

    /**
     * Remove a completed download (delete files and database entry).
     */
    suspend fun removeDownload(livesetId: Long) {
        withContext(Dispatchers.IO) {
            val entity = downloadDao.getByLivesetIdOnce(livesetId) ?: return@withContext

            // Remove from Media3 cache
            TfwDownloadService.sendRemoveDownload(context, entity.media3DownloadId, /* foreground */ false)

            // Database entry will be removed by DownloadTracker.onDownloadRemoved
        }
    }

    /**
     * Get the download status for a liveset as a reactive flow.
     */
    fun downloadStatus(livesetId: Long): Flow<DownloadStatus> {
        return downloadDao.getByLivesetId(livesetId).map { entity ->
            if (entity == null) {
                DownloadStatus.NotDownloaded
            } else {
                when (entity.state) {
                    DownloadState.QUEUED, DownloadState.DOWNLOADING -> {
                        DownloadStatus.Downloading(
                            progress = entity.progressPercent,
                            bytesDownloaded = entity.bytesDownloaded,
                            totalBytes = entity.totalBytes,
                        )
                    }
                    DownloadState.COMPLETED -> {
                        DownloadStatus.Completed(
                            quality = entity.quality,
                            fileSizeBytes = entity.audioFileSize,
                        )
                    }
                    DownloadState.FAILED -> {
                        DownloadStatus.Failed(entity.failureReason)
                    }
                    DownloadState.REMOVING -> {
                        DownloadStatus.NotDownloaded
                    }
                }
            }
        }
    }

    /**
     * Get download statuses for all livesets as a map (for list UI).
     */
    fun allDownloadStatuses(): Flow<Map<Long, DownloadStatus>> {
        return downloadDao.getAll().map { entities ->
            entities.associate { entity ->
                entity.livesetId to when (entity.state) {
                    DownloadState.QUEUED, DownloadState.DOWNLOADING -> {
                        DownloadStatus.Downloading(
                            progress = entity.progressPercent,
                            bytesDownloaded = entity.bytesDownloaded,
                            totalBytes = entity.totalBytes,
                        )
                    }
                    DownloadState.COMPLETED -> {
                        DownloadStatus.Completed(
                            quality = entity.quality,
                            fileSizeBytes = entity.audioFileSize,
                        )
                    }
                    DownloadState.FAILED -> {
                        DownloadStatus.Failed(entity.failureReason)
                    }
                    DownloadState.REMOVING -> {
                        DownloadStatus.NotDownloaded
                    }
                }
            }
        }
    }

    /**
     * Get IDs of all completed downloads.
     */
    fun completedLivesetIds(): Flow<Set<Long>> {
        return downloadDao.getCompletedLivesetIds().map { it.toSet() }
    }

    /**
     * Get the local cache URI for a downloaded liveset, if available.
     * This URI can be used for playback.
     */
    suspend fun getLocalUri(livesetId: Long): Uri? {
        return withContext(Dispatchers.IO) {
            val entity = downloadDao.getByLivesetIdOnce(livesetId)
            val filePath = entity?.audioFilePath
            if (entity?.state == DownloadState.COMPLETED && filePath != null) {
                filePath.toUri()
            } else {
                null
            }
        }
    }

    /**
     * Get the stored waveform JSON for a downloaded liveset.
     */
    suspend fun getWaveformJson(livesetId: Long): String? {
        return withContext(Dispatchers.IO) {
            downloadDao.getByLivesetIdOnce(livesetId)?.waveformJson
        }
    }

    /**
     * Get total storage used by downloads.
     */
    fun totalDownloadedBytes(): Flow<Long> {
        return downloadDao.getTotalDownloadedBytes().map { it ?: 0L }
    }

    /**
     * Get the download entity for a liveset.
     */
    fun getDownload(livesetId: Long): Flow<LivesetDownloadEntity?> {
        return downloadDao.getByLivesetId(livesetId)
    }

    /**
     * Fetch waveform JSON from the API.
     */
    private fun fetchWaveformJson(waveformUrl: String?): String? {
        if (waveformUrl.isNullOrBlank()) return null

        return try {
            val url = URL(waveformUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch waveform", e)
            null
        }
    }

    // LivesetCleanupCallback implementation

    /**
     * Called before livesets are deleted from the database.
     * Removes any associated downloads from cache and database.
     */
    override suspend fun onLivesetsRemoving(livesetIds: Set<Long>) {
        withContext(Dispatchers.IO) {
            for (livesetId in livesetIds) {
                try {
                    val entity = downloadDao.getByLivesetIdOnce(livesetId)
                    if (entity != null) {
                        Log.d(TAG, "Cleaning up download for liveset $livesetId before deletion")

                        // Remove from Media3 cache
                        TfwDownloadService.sendRemoveDownload(
                            context,
                            entity.media3DownloadId,
                            /* foreground */ false
                        )

                        // Remove from database
                        downloadDao.deleteByLivesetId(livesetId)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to cleanup download for liveset $livesetId", e)
                }
            }
        }
    }
}
