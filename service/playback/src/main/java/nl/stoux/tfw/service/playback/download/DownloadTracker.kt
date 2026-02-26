package nl.stoux.tfw.service.playback.download

import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.database.dao.LivesetDownloadDao
import nl.stoux.tfw.core.common.database.entity.DownloadState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks Media3 DownloadManager state and syncs it to the Room database.
 * Provides a reactive stream of download changes for UI updates.
 */
@UnstableApi
@Singleton
class DownloadTracker @Inject constructor(
    private val downloadManager: DownloadManager,
    private val downloadDao: LivesetDownloadDao,
) : DownloadManager.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    /** Map of Media3 download IDs to Download objects */
    val downloads: StateFlow<Map<String, Download>> = _downloads

    private var progressPollingJob: Job? = null

    init {
        downloadManager.addListener(this)
        // Load initial downloads
        refreshDownloads()
        // Start polling for progress updates
        startProgressPolling()
    }

    /**
     * Polls the DownloadManager for progress updates every 500ms.
     * Media3's onDownloadChanged doesn't fire frequently enough for smooth progress UI.
     */
    private fun startProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = scope.launch {
            while (isActive) {
                delay(500)
                pollDownloadProgress()
            }
        }
    }

    private suspend fun pollDownloadProgress() {
        val downloadIndex = downloadManager.downloadIndex
        downloadIndex.getDownloads(
            Download.STATE_DOWNLOADING,
            Download.STATE_QUEUED
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val download = cursor.download
                val downloadId = download.request.id

                // Update in-memory map
                _downloads.value = _downloads.value.toMutableMap().apply {
                    put(downloadId, download)
                }

                // Sync progress to database
                if (download.state == Download.STATE_DOWNLOADING) {
                    downloadDao.updateProgress(
                        downloadId = downloadId,
                        progress = download.percentDownloaded,
                        bytesDownloaded = download.bytesDownloaded,
                        totalBytes = download.contentLength,
                        state = DownloadState.DOWNLOADING,
                    )
                }
            }
        }
    }

    private fun refreshDownloads() {
        val downloadIndex = downloadManager.downloadIndex
        val currentDownloads = mutableMapOf<String, Download>()
        downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                val download = cursor.download
                currentDownloads[download.request.id] = download
            }
        }
        _downloads.value = currentDownloads
    }

    override fun onDownloadChanged(
        downloadManager: DownloadManager,
        download: Download,
        finalException: Exception?,
    ) {
        val downloadId = download.request.id
        Log.d("DownloadTracker", "Download changed: $downloadId, state=${download.state}, progress=${download.percentDownloaded}%")

        // Update in-memory map
        _downloads.value = _downloads.value.toMutableMap().apply {
            put(downloadId, download)
        }

        // Sync to database
        scope.launch {
            syncDownloadToDb(download, finalException)
        }
    }

    override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
        val downloadId = download.request.id
        Log.d("DownloadTracker", "Download removed: $downloadId")

        // Remove from in-memory map
        _downloads.value = _downloads.value.toMutableMap().apply {
            remove(downloadId)
        }

        // Remove from database
        scope.launch {
            downloadDao.deleteByMedia3DownloadId(downloadId)
        }
    }

    private suspend fun syncDownloadToDb(download: Download, finalException: Exception?) {
        val downloadId = download.request.id

        when (download.state) {
            Download.STATE_QUEUED -> {
                // Already added when starting download
            }

            Download.STATE_DOWNLOADING -> {
                downloadDao.updateProgress(
                    downloadId = downloadId,
                    progress = download.percentDownloaded,
                    bytesDownloaded = download.bytesDownloaded,
                    totalBytes = download.contentLength,
                    state = DownloadState.DOWNLOADING,
                )
            }

            Download.STATE_COMPLETED -> {
                val entity = downloadDao.getByMedia3DownloadId(downloadId)
                if (entity != null) {
                    // Get the cached file path
                    val cachedUri = download.request.uri.toString()
                    downloadDao.markCompleted(
                        downloadId = downloadId,
                        completedAt = System.currentTimeMillis(),
                        audioFilePath = cachedUri,
                        audioFileSize = download.bytesDownloaded,
                        waveformJson = entity.waveformJson, // Keep existing waveform
                    )
                }
            }

            Download.STATE_FAILED -> {
                val reason = finalException?.message ?: "Unknown error"
                downloadDao.markFailed(downloadId, reason)
            }

            Download.STATE_REMOVING -> {
                // Will be handled in onDownloadRemoved
            }

            Download.STATE_STOPPED -> {
                // Download was paused/stopped - keep current state
            }

            Download.STATE_RESTARTING -> {
                downloadDao.updateProgress(
                    downloadId = downloadId,
                    progress = 0f,
                    bytesDownloaded = 0,
                    totalBytes = download.contentLength,
                    state = DownloadState.QUEUED,
                )
            }
        }
    }

    /**
     * Check if a download exists for the given ID.
     */
    fun isDownloaded(downloadId: String): Boolean {
        val download = _downloads.value[downloadId]
        return download?.state == Download.STATE_COMPLETED
    }

    /**
     * Get the download state for a given ID.
     */
    fun getDownloadState(downloadId: String): Download? {
        return _downloads.value[downloadId]
    }
}
