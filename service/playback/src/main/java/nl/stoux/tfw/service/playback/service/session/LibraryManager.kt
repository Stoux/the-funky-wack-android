package nl.stoux.tfw.service.playback.service.session

import android.annotation.SuppressLint
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nl.stoux.tfw.core.common.database.dao.EditionWithContent
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.repository.EditionRepository
import nl.stoux.tfw.core.common.database.entity.artworkUrl
import javax.inject.Inject
import javax.inject.Singleton

fun interface LibraryChildrenChangedCallback {
    fun onChildrenChanged(parentId: String, itemCount: Int)
}

enum class RefreshState { IDLE, REFRESHING, DONE }

@Singleton
class LibraryManager @Inject constructor(
    private val editionRepository: EditionRepository,
    private val sessionSettings: SessionSettings,
) {

    // Initialize to current time to prevent refreshIfStale() from triggering during startup race
    // The actual refresh in init() will update this to the real completion time
    @Volatile
    private var lastRefreshTimeMs: Long = System.currentTimeMillis()
    @Volatile
    private var isRefreshing: Boolean = false
    private var refreshState: RefreshState = RefreshState.IDLE
    var childrenChangedCallback: LibraryChildrenChangedCallback? = null

    suspend fun init() {
        if (isRefreshing) return
        isRefreshing = true
        try {
            editionRepository.refreshEditions()
            lastRefreshTimeMs = System.currentTimeMillis()
        } finally {
            isRefreshing = false
        }
    }

    /**
     * Refresh editions if the data is stale (older than [maxAgeMinutes]).
     * Useful for auto-refreshing when Automotive reconnects after car wake.
     * @return true if a refresh was triggered, false if data was fresh
     */
    suspend fun refreshIfStale(maxAgeMinutes: Int = 720): Boolean {
        val ageMs = System.currentTimeMillis() - lastRefreshTimeMs
        val maxAgeMs = maxAgeMinutes * 60 * 1000L
        return if (ageMs > maxAgeMs) {
            init()
            true
        } else {
            false
        }
    }

    /**
     * Get the root MediaItem used to register the app in the Media Library
     */
    fun getRoot(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(CustomMediaId.ROOT.original)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("The Funky Wack")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    /**
     * Get the visible (paginated) items for the given media ID
     * Allows for nested navigation based on the parent ID.
     */
    fun getChildren(
        parentId: String,
        page: Int,
        pageSize: Int,
    ): List<MediaItem> {
        Log.d("LibraryManager", "Getting children for ${parentId}")
        val mediaId = CustomMediaId.from(parentId)


        return when {
            // Menus
            mediaId == CustomMediaId.ROOT -> getRootChildren()
            mediaId == CustomMediaId.ROOT_EDITIONS -> getEditions(page, pageSize)
            mediaId == CustomMediaId.ROOT_LIVESETS -> getAllLivesets(page, pageSize)
            mediaId == CustomMediaId.ROOT_SETTINGS -> getSettings(page, pageSize)
            mediaId == CustomMediaId.SETTINGS_REFRESH -> getRefreshStatus()

            // Show items inside another item
            mediaId.isEdition() -> getEditionLivesets(mediaId, page, pageSize)
            mediaId.isLiveset() -> getLivesetTracks(mediaId, page, pageSize)

            else -> emptyList()
        }
    }

    private fun getRootChildren(): List<MediaItem> {
        return listOf(
            browsableMediaItem(CustomMediaId.ROOT_EDITIONS, "Editions"),
            browsableMediaItem(CustomMediaId.ROOT_LIVESETS, "All livesets"),
            browsableMediaItem(CustomMediaId.ROOT_SETTINGS, "Settings"),
        )
    }

    fun getRootChildrenCount(): Int = 3 // Editions, All livesets, Settings

    private fun getSettings(page: Int, pageSize: Int): List<MediaItem> {
        return listOf(
            MediaItem.Builder()
                .setMediaId(CustomMediaId.SETTINGS_REFRESH.original)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Refresh livesets")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
        )
    }

    private fun getRefreshStatus(): List<MediaItem> {
        // Trigger refresh if idle
        if (refreshState == RefreshState.IDLE) {
            refreshState = RefreshState.REFRESHING
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { editionRepository.refreshEditions() }
                lastRefreshTimeMs = System.currentTimeMillis()
                refreshState = RefreshState.DONE
                // Notify that children changed so UI updates
                childrenChangedCallback?.onChildrenChanged(
                    CustomMediaId.SETTINGS_REFRESH.original,
                    1
                )
            }
        }

        val (title, subtitle) = when (refreshState) {
            RefreshState.IDLE -> "Tap to refresh" to null
            RefreshState.REFRESHING -> "Refreshing..." to "Please wait"
            RefreshState.DONE -> "Done!" to "Press back to continue"
        }

        // Reset to idle after showing "Done" so next time it can refresh again
        if (refreshState == RefreshState.DONE) {
            refreshState = RefreshState.IDLE
        }

        return listOf(
            MediaItem.Builder()
                .setMediaId("settings.refresh.status")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(subtitle)
                        .setIsBrowsable(false)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
        )
    }

    private fun getEditions(page: Int, pageSize: Int): List<MediaItem> {
        sessionSettings.queueEditionLivesetsOnly = true
        val editions = paginate(loadEditions(), page, pageSize)


        return editions.map { ed ->
            MediaItem.Builder()
                .setMediaId("edition:${ed.edition.id}")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("TFW #${ed.edition.number}: ${ed.edition.tagLine}")
                        .setArtist("${ed.edition.date} - ${ed.livesets.size} liveset${if (ed.livesets.size == 1)  "" else "s"}")
                        .setArtworkUri(ed.edition.artworkUrl?.toUri())
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
        }
    }

    private fun getAllLivesets(page: Int, pageSize: Int): List<MediaItem> {
        sessionSettings.queueEditionLivesetsOnly = false
        val livesets = runBlocking { editionRepository.getLivesets(page, pageSize).first() }

        return livesets.map { liveset -> livesetMediaItem(liveset, true) }
    }

    private fun getEditionLivesets(mediaId: CustomMediaId, page: Int, pageSize: Int): List<MediaItem> {
        val editionId = mediaId.getEditionId() ?: throw IllegalArgumentException("Invalid Edition ID Media ID passed")
        val livesets = runBlocking { editionRepository.getLivesets(editionId, page, pageSize).first() }
        return livesets.map { liveset -> livesetMediaItem(liveset) }
    }

    @SuppressLint("DefaultLocale")
    private fun getLivesetTracks(mediaId: CustomMediaId, page: Int, pageSize: Int): List<MediaItem> {
        val livesetId = mediaId.getLivesetId() ?: throw IllegalArgumentException("Invalid Liveset ID Media ID passed")
        val liveset = runBlocking { editionRepository.findLiveset(livesetId).first() } ?: throw IllegalArgumentException("Liveset not found")

        return liveset.tracks.map { track ->
            val trackMediaId = CustomMediaId.forEntity(track)
            val timestampSec = track.timestampSec
            val timestamp = when {
                timestampSec != null -> {
                    val hours = timestampSec / 3600
                    val minutes = (timestampSec % 3600) / 60
                    val seconds = timestampSec % 60
                    if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
                    else String.format("%02d:%02d", minutes, seconds)
                } else -> "N/A"
            }

            MediaItem.Builder()
                .setMediaId(trackMediaId.original)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("#${track.orderInSet}: ${track.title}")
                        .setArtist("Timestamp: $timestamp")
                        .setIsBrowsable(false)
                        .setIsPlayable(track.timestampSec != null)
                        .build()
                )
                .build()
        }
    }


    private fun browsableMediaItem(
        mediaId: CustomMediaId,
        title: String,
        additionalMetadata: (MediaMetadata.Builder.() -> Unit)? = null
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId.original)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .apply { additionalMetadata?.invoke(this) }
                    .build()
            )
            .build()
    }

    fun getQueueBasedForLiveset(id: Long): List<MediaItem> {
        // Find the liveset
        val liveset = runBlocking { editionRepository.findLiveset(id).first() }
        if (liveset == null) throw IllegalArgumentException("Unknown liveset: $id")

        // Fetch all other livesets that should be in the queue
        val livesetQueue = when {
            sessionSettings.queueEditionLivesetsOnly -> runBlocking { editionRepository.getLivesets(liveset.edition.id).first() }
            else -> runBlocking { editionRepository.getLivesets().first() }
        }.filter { it ->
            // Filter to only playable livesets
            val liveset = it.liveset
            (liveset.hqUrl ?: liveset.lqUrl ?: liveset.losslessUrl) != null
        }


        // Make our liveset the zero-index for the list (by moving all before it to the end)
        val ourLivesetIndex = livesetQueue.indexOfFirst { it.liveset.id == liveset.liveset.id }
        if (ourLivesetIndex == -1) {
            // Should not happen, but if it does, just return the liveset
            return listOf(livesetMediaItem(liveset))
        }

        val reorderedQueue = mutableListOf<LivesetWithDetails>()
        reorderedQueue.add(livesetQueue[ourLivesetIndex]) // Our liveset is first
        reorderedQueue.addAll(livesetQueue.subList(ourLivesetIndex + 1, livesetQueue.size)) // Add all after
        reorderedQueue.addAll(livesetQueue.subList(0, ourLivesetIndex)) // Add all before

        return reorderedQueue.map { livesetMediaItem(it) }
    }

    fun livesetMediaItem(
        id: Long,
    ): MediaItem {
        val lwd = runBlocking { editionRepository.findLiveset(id).first() }
        if (lwd == null) throw IllegalArgumentException("Unknown liveset: $id")

        return livesetMediaItem(lwd)
    }

    private fun livesetMediaItem(
        lwd: LivesetWithDetails,
        showEditionInfo: Boolean = false,
    ): MediaItem {
        // Use centralized builder for consistent MediaItem construction
        return PlayableMediaItemBuilder.build(lwd, showEditionInfo)
            ?: fallbackNonPlayableMediaItem(lwd, showEditionInfo)
    }

    /**
     * Fallback for livesets without playable URLs (browse-only).
     */
    private fun fallbackNonPlayableMediaItem(
        lwd: LivesetWithDetails,
        showEditionInfo: Boolean,
    ): MediaItem {
        val mediaId = CustomMediaId.forEntity(lwd.liveset)
        var title = lwd.liveset.title
        var artist = lwd.liveset.artistName
        if (showEditionInfo) {
            title = "#${lwd.liveset.lineupOrder ?: "?"} $title"
            artist = "TFW #${lwd.edition.number} - $artist"
        }

        return MediaItem.Builder()
            .setMediaId(mediaId.original)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setTrackNumber(lwd.liveset.lineupOrder)
                    .setAlbumTitle("TFW #${lwd.edition.number}: ${lwd.edition.tagLine}")
                    .setArtworkUri(lwd.edition.artworkUrl?.toUri())
                    .setIsBrowsable(lwd.tracks.any { it.timestampSec != null })
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }


    private fun loadEditions(): List<EditionWithContent> =
        runBlocking { editionRepository.getEditions().first() }



    private fun <T> paginate(list: List<T>, page: Int, pageSize: Int): List<T> {
        val startIndex = page * pageSize
        if (startIndex >= list.size) {
            return emptyList()
        }
        val endIndex = (startIndex + pageSize).coerceAtMost(list.size)
        return list.subList(startIndex, endIndex)
    }

    /**
     * Search for livesets and editions matching the query.
     * Returns livesets first, then editions.
     */
    fun search(query: String, page: Int, pageSize: Int): List<MediaItem> {
        if (query.isBlank()) return emptyList()

        val results = mutableListOf<MediaItem>()

        // Search livesets
        val livesets = runBlocking { editionRepository.searchLivesets(query, limit = 15).first() }
        results.addAll(livesets.map { livesetMediaItem(it, showEditionInfo = true) })

        // Search editions
        val editions = runBlocking { editionRepository.searchEditions(query, limit = 5).first() }
        results.addAll(editions.map { ed ->
            MediaItem.Builder()
                .setMediaId("edition:${ed.edition.id}")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("TFW #${ed.edition.number}: ${ed.edition.tagLine}")
                        .setArtist("${ed.edition.date} - ${ed.livesets.size} liveset${if (ed.livesets.size == 1) "" else "s"}")
                        .setArtworkUri(ed.edition.artworkUrl?.toUri())
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
        })

        return paginate(results, page, pageSize)
    }

}