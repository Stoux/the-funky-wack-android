package nl.stoux.tfw.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.stoux.tfw.core.common.database.dao.EditionWithContent
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.repository.EditionRepository
import nl.stoux.tfw.service.playback.download.DownloadRepository
import nl.stoux.tfw.service.playback.download.DownloadResult
import nl.stoux.tfw.service.playback.download.DownloadStatus
import nl.stoux.tfw.service.playback.download.NetworkStateMonitor
import nl.stoux.tfw.service.playback.download.NetworkType
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository.AudioQuality
import java.net.HttpURLConnection
import java.net.URL

/**
 * Info about a single quality option with actual file details.
 */
data class QualityOptionInfo(
    val quality: AudioQuality,
    val url: String,
    val format: String,
    val fileSize: Long?, // null if unknown/failed to fetch
)

/**
 * Data class for a liveset with its available qualities and file info.
 */
data class LivesetDownloadInfo(
    val liveset: LivesetWithDetails,
    val qualityOptions: List<QualityOptionInfo>,
)

@HiltViewModel
class EditionListViewModel @Inject constructor(
    private val repository: EditionRepository,
    private val downloadRepository: DownloadRepository,
    private val networkStateMonitor: NetworkStateMonitor,
    private val playbackSettings: PlaybackSettingsRepository,
) : ViewModel() {

    // Download statuses for all livesets
    val downloadStatuses: StateFlow<Map<Long, DownloadStatus>> = downloadRepository
        .allDownloadStatuses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Network state
    val networkState: StateFlow<NetworkType> = networkStateMonitor.networkState

    // Offline mode
    val offlineMode: StateFlow<Boolean> = playbackSettings.offlineMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Completed download IDs (for offline mode filtering)
    private val completedDownloads: StateFlow<Set<Long>> = downloadRepository
        .completedLivesetIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // Default audio quality for pre-selection
    val defaultQuality: StateFlow<AudioQuality> = playbackSettings.audioQuality()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioQuality.HIGH)

    // Editions filtered based on offline mode
    val editions: StateFlow<List<EditionWithContent>> = combine(
        repository.getEditions(),
        offlineMode,
        completedDownloads,
    ) { editions, isOffline, downloaded ->
        if (isOffline) {
            // Filter to only show editions with at least one downloaded liveset
            editions.mapNotNull { edition ->
                val downloadedLivesets = edition.livesets.filter { it.liveset.id in downloaded }
                if (downloadedLivesets.isEmpty()) {
                    null
                } else {
                    edition.copy(livesets = downloadedLivesets)
                }
            }
        } else {
            editions
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Dialog state
    private val _showDownloadDialog = MutableStateFlow<LivesetDownloadInfo?>(null)
    val showDownloadDialog: StateFlow<LivesetDownloadInfo?> = _showDownloadDialog

    private val _showCellularWarning = MutableStateFlow(false)
    val showCellularWarning: StateFlow<Boolean> = _showCellularWarning

    private var pendingDownload: Pair<Long, AudioQuality>? = null

    init {
        // Kick off a refresh on startup (stale-while-revalidate)
        viewModelScope.launch {
            repository.refreshEditions()
        }
    }

    /**
     * Show the download quality selection dialog for a liveset.
     * Fetches actual file sizes via HEAD requests.
     */
    fun showDownloadQualityDialog(livesetId: Long) {
        viewModelScope.launch {
            val editions = editions.value
            val liveset = editions.flatMap { it.livesets }.find { it.liveset.id == livesetId }
            if (liveset != null) {
                // Build list of quality -> URL pairs
                val qualityUrls = buildList {
                    liveset.liveset.lqUrl?.takeIf { it.isNotBlank() }?.let {
                        add(AudioQuality.LOW to it)
                    }
                    liveset.liveset.hqUrl?.takeIf { it.isNotBlank() }?.let {
                        add(AudioQuality.HIGH to it)
                    }
                    liveset.liveset.losslessUrl?.takeIf { it.isNotBlank() }?.let {
                        add(AudioQuality.LOSSLESS to it)
                    }
                }

                // Fetch file sizes in parallel
                val qualityOptions = withContext(Dispatchers.IO) {
                    qualityUrls.map { (quality, url) ->
                        async {
                            val format = extractFormat(url)
                            val size = fetchFileSize(url)
                            QualityOptionInfo(quality, url, format, size)
                        }
                    }.awaitAll()
                }

                _showDownloadDialog.value = LivesetDownloadInfo(liveset, qualityOptions)
            }
        }
    }

    /**
     * Extract the audio format from a URL's file extension.
     */
    private fun extractFormat(url: String): String {
        val extension = url.substringAfterLast('.', "").substringBefore('?').lowercase()
        return when (extension) {
            "mp3" -> "MP3"
            "m4a" -> "M4A"
            "aac" -> "AAC"
            "flac" -> "FLAC"
            "wav" -> "WAV"
            "ogg", "oga" -> "OGG"
            "opus" -> "Opus"
            else -> extension.uppercase().ifEmpty { "Audio" }
        }
    }

    /**
     * Fetch file size via HTTP HEAD request.
     * Returns null if the size cannot be determined.
     */
    private fun fetchFileSize(url: String): Long? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.connect()

            val contentLength = connection.contentLengthLong
            connection.disconnect()

            if (contentLength > 0) contentLength else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Dismiss the download dialog.
     */
    fun dismissDownloadDialog() {
        _showDownloadDialog.value = null
    }

    /**
     * Start downloading a liveset with the specified quality.
     * Shows cellular warning if on mobile data.
     */
    fun startDownload(livesetId: Long, quality: AudioQuality) {
        val isCellular = networkState.value == NetworkType.CELLULAR

        if (isCellular) {
            pendingDownload = livesetId to quality
            _showCellularWarning.value = true
        } else {
            performDownload(livesetId, quality)
        }
        _showDownloadDialog.value = null
    }

    /**
     * Confirm download on cellular.
     */
    fun confirmCellularDownload() {
        _showCellularWarning.value = false
        pendingDownload?.let { (id, quality) ->
            performDownload(id, quality)
        }
        pendingDownload = null
    }

    /**
     * Cancel cellular download warning.
     */
    fun cancelCellularWarning() {
        _showCellularWarning.value = false
        pendingDownload = null
    }

    private fun performDownload(livesetId: Long, quality: AudioQuality) {
        viewModelScope.launch {
            val result = downloadRepository.startDownload(livesetId, quality)
            if (result is DownloadResult.Error) {
                // Could show error toast/snackbar here
            }
        }
    }

    /**
     * Cancel an in-progress download.
     */
    fun cancelDownload(livesetId: Long) {
        viewModelScope.launch {
            downloadRepository.cancelDownload(livesetId)
        }
    }

    /**
     * Delete a completed download.
     */
    fun deleteDownload(livesetId: Long) {
        viewModelScope.launch {
            downloadRepository.removeDownload(livesetId)
        }
    }
}
