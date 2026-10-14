package nl.stoux.tfw.service.playback.service.session

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.database.dao.EditionWithContent
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.network.dto.auth.PlaylistDto
import nl.stoux.tfw.core.common.repository.EditionRepository
import nl.stoux.tfw.core.common.repository.FavoritesRepository
import nl.stoux.tfw.core.common.repository.PlaybackPositionRepository
import nl.stoux.tfw.core.common.repository.PlaylistRepository
import nl.stoux.tfw.core.common.database.entity.artworkUrl
import javax.inject.Inject
import javax.inject.Singleton

fun interface LibraryChildrenChangedCallback {
    fun onChildrenChanged(parentId: String, itemCount: Int)
}

enum class RefreshState { IDLE, REFRESHING, DONE }

/** MediaMetadata extra carrying the source playlist share code for media-library items. */
const val EXTRA_PLAYLIST_SHARE_CODE = "tfw.source_playlist_share_code"

/** MediaMetadata extra marking that the item came from the favorites shelf. */
const val EXTRA_FROM_FAVORITES = "tfw.source_favorites"

@Singleton
class LibraryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val editionRepository: EditionRepository,
    private val sessionSettings: SessionSettings,
    private val authRepository: AuthRepository,
    private val favoritesRepository: FavoritesRepository,
    private val playlistRepository: PlaylistRepository,
    private val playbackPositionRepository: PlaybackPositionRepository,
) {

    private val isAutomotive: Boolean by lazy {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    }

    // Initialize to current time to prevent refreshIfStale() from triggering during startup race
    // The actual refresh in init() will update this to the real completion time
    @Volatile
    private var lastRefreshTimeMs: Long = System.currentTimeMillis()
    @Volatile
    private var isRefreshing: Boolean = false
    private var refreshState: RefreshState = RefreshState.IDLE
    var childrenChangedCallback: LibraryChildrenChangedCallback? = null

    init {
        // Browse screens opened before or during an editions refresh would otherwise keep showing stale data
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            editionRepository.cachedEditions
                .drop(1)
                .distinctUntilChanged()
                .collect { editions -> notifyCatalogChanged(editions) }
        }
    }

    private fun notifyCatalogChanged(editions: List<EditionWithContent>) {
        val callback = childrenChangedCallback ?: return
        callback.onChildrenChanged(CustomMediaId.ROOT_EDITIONS.original, editions.size)
        callback.onChildrenChanged(CustomMediaId.ROOT_LIVESETS.original, editions.sumOf { it.livesets.size })
        editions.forEach { ed ->
            callback.onChildrenChanged("edition:${ed.edition.id}", ed.livesets.size)
        }
    }

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
            mediaId == CustomMediaId.ROOT_FAVORITES -> getFavoriteLivesets(page, pageSize)
            mediaId == CustomMediaId.ROOT_PLAYLISTS -> getPlaylists(page, pageSize)
            mediaId == CustomMediaId.ROOT_CONTINUE -> getContinueListening(page, pageSize)
            mediaId == CustomMediaId.ROOT_SETTINGS -> getSettings(page, pageSize)
            mediaId == CustomMediaId.SETTINGS_REFRESH -> getRefreshStatus()
            mediaId == CustomMediaId.SETTINGS_PAIR -> handlePairAction()
            mediaId == CustomMediaId.SETTINGS_SIGN_OUT -> handleSignOutAction()

            // Show items inside another item
            mediaId.isEdition() -> getEditionLivesets(mediaId, page, pageSize)
            mediaId.isLiveset() -> getLivesetTracks(mediaId, page, pageSize)
            mediaId.isPlaylist() -> getPlaylistItems(mediaId, page, pageSize)

            else -> emptyList()
        }
    }

    private fun getRootChildren(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        // "Continue listening" goes first when logged in and there's something to resume
        if (authRepository.isLoggedIn.value && playbackPositionRepository.positions.value.isNotEmpty()) {
            items.add(browsableMediaItem(CustomMediaId.ROOT_CONTINUE, "Continue listening"))
        }
        items.add(browsableMediaItem(CustomMediaId.ROOT_EDITIONS, "Editions"))
        items.add(browsableMediaItem(CustomMediaId.ROOT_LIVESETS, "All livesets"))
        // Favorites and Playlists are personalized — only show when logged in
        if (authRepository.isLoggedIn.value) {
            items.add(browsableMediaItem(CustomMediaId.ROOT_FAVORITES, "Favorites"))
            items.add(browsableMediaItem(CustomMediaId.ROOT_PLAYLISTS, "Playlists"))
        }
        items.add(browsableMediaItem(CustomMediaId.ROOT_SETTINGS, "Settings"))
        return items
    }

    fun getRootChildrenCount(): Int {
        var count = 3 // Editions, All livesets, Settings
        if (authRepository.isLoggedIn.value) {
            count += 2 // Favorites + Playlists
            if (playbackPositionRepository.positions.value.isNotEmpty()) count += 1 // Continue
        }
        return count
    }

    private fun getSettings(page: Int, pageSize: Int): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        items.add(actionItem(CustomMediaId.SETTINGS_REFRESH, "Refresh livesets"))
        if (isAutomotive) {
            if (authRepository.isLoggedIn.value) {
                items.add(actionItem(CustomMediaId.SETTINGS_SIGN_OUT, "Sign out"))
            } else {
                items.add(actionItem(CustomMediaId.SETTINGS_PAIR, "Pair this car"))
            }
        }
        return items
    }

    private fun actionItem(id: CustomMediaId, title: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id.original)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    private fun handlePairAction(): List<MediaItem> {
        val intent = Intent("nl.stoux.tfw.automotive.action.PAIR").apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w("LibraryManager", "Failed to launch pair activity", it) }
        return listOf(statusItem("settings.pair.status", "Follow instructions on screen"))
    }

    private fun handleSignOutAction(): List<MediaItem> {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { authRepository.logout() }
            childrenChangedCallback?.onChildrenChanged(
                CustomMediaId.ROOT_SETTINGS.original,
                getSettings(0, Int.MAX_VALUE).size,
            )
            childrenChangedCallback?.onChildrenChanged(
                CustomMediaId.ROOT.original,
                getRootChildrenCount(),
            )
        }
        return listOf(statusItem("settings.signout.status", "Signed out"))
    }

    private fun statusItem(mediaId: String, title: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(false)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
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

    private fun getFavoriteLivesets(page: Int, pageSize: Int): List<MediaItem> {
        val favoriteIds = favoritesRepository.favoriteLivesetIds.value
        val livesets = favoriteIds.mapNotNull { editionRepository.getCachedLiveset(it) }
        return paginate(livesets, page, pageSize).map { liveset ->
            // Tag each item so onAddMediaItems can build a favorites-ordered queue
            // when the user taps from Android Auto.
            val base = livesetMediaItem(liveset, showEditionInfo = true)
            val extras = (base.mediaMetadata.extras ?: Bundle()).apply {
                putBoolean(EXTRA_FROM_FAVORITES, true)
            }
            val metadata = base.mediaMetadata.buildUpon().setExtras(extras).build()
            base.buildUpon().setMediaMetadata(metadata).build()
        }
    }

    /**
     * Queue ordering when the user starts playback from the favorites shelf:
     * the favorites list itself, rotated so the tapped liveset comes first.
     * Falls back to edition-context if the favorite isn't cached yet.
     */
    fun getQueueBasedForFavorites(startLivesetId: Long): List<MediaItem> {
        val favoriteIds = favoritesRepository.favoriteLivesetIds.value
        if (favoriteIds.isEmpty()) return getQueueBasedForLiveset(startLivesetId)
        val livesets = favoriteIds.mapNotNull { editionRepository.getCachedLiveset(it) }
            .filter { (it.liveset.hqUrl ?: it.liveset.lqUrl ?: it.liveset.losslessUrl) != null }
        val startIdx = livesets.indexOfFirst { it.liveset.id == startLivesetId }
        if (startIdx == -1) return getQueueBasedForLiveset(startLivesetId)

        val reordered = mutableListOf<LivesetWithDetails>()
        reordered.add(livesets[startIdx])
        reordered.addAll(livesets.subList(startIdx + 1, livesets.size))
        reordered.addAll(livesets.subList(0, startIdx))
        return reordered.map { livesetMediaItem(it) }
    }

    private fun getPlaylists(page: Int, pageSize: Int): List<MediaItem> {
        sessionSettings.queueEditionLivesetsOnly = false
        val playlists = playlistRepository.playlists.value
        // Trigger a refresh in the background so subsequent navigations are fresher
        if (playlists.isEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { playlistRepository.refresh() }
                childrenChangedCallback?.onChildrenChanged(
                    CustomMediaId.ROOT_PLAYLISTS.original,
                    playlistRepository.playlists.value.size,
                )
            }
        }
        return paginate(playlists, page, pageSize).map { playlistMediaItem(it) }
    }

    private fun getPlaylistItems(mediaId: CustomMediaId, page: Int, pageSize: Int): List<MediaItem> {
        val shareCode = mediaId.getPlaylistShareCode() ?: return emptyList()
        val cachedIds = playlistRepository.cachedItemLivesetIds(shareCode)

        // Fire an async refresh so subsequent navigations get the latest order
        // and items; notify the library when fresh items land.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { playlistRepository.getPlaylist(shareCode) }
            childrenChangedCallback?.onChildrenChanged(
                mediaId.original,
                playlistRepository.cachedItemLivesetIds(shareCode).size,
            )
        }

        val livesets = cachedIds.mapNotNull { editionRepository.getCachedLiveset(it) }
        return paginate(livesets, page, pageSize).map { lwd ->
            // Tag each item with the source playlist so onAddMediaItems can build
            // a playlist-ordered queue when the user taps from Android Auto.
            val base = livesetMediaItem(lwd, showEditionInfo = true)
            val extras = (base.mediaMetadata.extras ?: Bundle()).apply {
                putString(EXTRA_PLAYLIST_SHARE_CODE, shareCode)
            }
            val metadata = base.mediaMetadata.buildUpon().setExtras(extras).build()
            base.buildUpon().setMediaMetadata(metadata).build()
        }
    }

    /**
     * Queue ordering when the user starts playback from inside a playlist:
     * the playlist's own items, rotated so the tapped liveset comes first.
     * Falls back to edition-context if the playlist isn't cached yet.
     */
    fun getQueueBasedForPlaylist(shareCode: String, startLivesetId: Long): List<MediaItem> {
        val livesetIds = playlistRepository.cachedItemLivesetIds(shareCode)
        if (livesetIds.isEmpty()) return getQueueBasedForLiveset(startLivesetId)
        val livesets = livesetIds.mapNotNull { editionRepository.getCachedLiveset(it) }
            .filter { (it.liveset.hqUrl ?: it.liveset.lqUrl ?: it.liveset.losslessUrl) != null }
        val startIdx = livesets.indexOfFirst { it.liveset.id == startLivesetId }
        if (startIdx == -1) return getQueueBasedForLiveset(startLivesetId)

        val reordered = mutableListOf<LivesetWithDetails>()
        reordered.add(livesets[startIdx])
        reordered.addAll(livesets.subList(startIdx + 1, livesets.size))
        reordered.addAll(livesets.subList(0, startIdx))
        return reordered.map { livesetMediaItem(it) }
    }

    private fun playlistMediaItem(playlist: PlaylistDto): MediaItem {
        val count = playlist.itemsCount
        val itemsLabel = "$count item${if (count == 1) "" else "s"}"
        return MediaItem.Builder()
            .setMediaId(CustomMediaId.forPlaylist(playlist.shareCode).original)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(playlist.name)
                    .setArtist(itemsLabel)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    private fun getContinueListening(page: Int, pageSize: Int): List<MediaItem> {
        sessionSettings.queueEditionLivesetsOnly = false
        val positions = playbackPositionRepository.positions.value
        val mapped = positions.mapNotNull { pos ->
            val liveset = editionRepository.getCachedLiveset(pos.livesetId) ?: return@mapNotNull null
            liveset to pos.position
        }
        return paginate(mapped, page, pageSize).map { (lwd, positionSec) ->
            val baseMedia = livesetMediaItem(lwd, showEditionInfo = true)
            // Override mediaId to encode the resume position so playback jumps to the right spot
            val newMediaId = "liveset:${lwd.liveset.id}@${positionSec}"
            baseMedia.buildUpon().setMediaId(newMediaId).build()
        }
    }

    private fun getAllLivesets(page: Int, pageSize: Int): List<MediaItem> {
        sessionSettings.queueEditionLivesetsOnly = false
        val livesets = paginate(editionRepository.cachedLivesets.value, page, pageSize)
        return livesets.map { liveset -> livesetMediaItem(liveset, true) }
    }

    private fun getEditionLivesets(mediaId: CustomMediaId, page: Int, pageSize: Int): List<MediaItem> {
        val editionId = mediaId.getEditionId() ?: throw IllegalArgumentException("Invalid Edition ID Media ID passed")
        val livesets = editionRepository.cachedLivesets.value.filter { it.edition.id == editionId }
        return paginate(livesets, page, pageSize).map { liveset -> livesetMediaItem(liveset) }
    }

    @SuppressLint("DefaultLocale")
    private fun getLivesetTracks(mediaId: CustomMediaId, page: Int, pageSize: Int): List<MediaItem> {
        val livesetId = mediaId.getLivesetId() ?: throw IllegalArgumentException("Invalid Liveset ID Media ID passed")
        val liveset = editionRepository.getCachedLiveset(livesetId) ?: throw IllegalArgumentException("Liveset not found")

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
        val liveset = editionRepository.getCachedLiveset(id)
            ?: throw IllegalArgumentException("Unknown liveset: $id")

        // Fetch all other livesets that should be in the queue
        val allLivesets = editionRepository.cachedLivesets.value
        val livesetQueue = when {
            sessionSettings.queueEditionLivesetsOnly -> allLivesets.filter { it.edition.id == liveset.edition.id }
            else -> allLivesets
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
        val lwd = editionRepository.getCachedLiveset(id)
            ?: throw IllegalArgumentException("Unknown liveset: $id")
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
        val title = PlayableMediaItemBuilder.formatTitle(lwd, showEditionInfo)
        val artist = PlayableMediaItemBuilder.formatArtist(lwd, showEditionInfo)

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
        editionRepository.cachedEditions.value



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
        val queryLower = query.lowercase()

        // Search livesets from cache
        val livesets = editionRepository.cachedLivesets.value
            .filter { lwd ->
                lwd.liveset.title.lowercase().contains(queryLower) ||
                lwd.liveset.artistName.lowercase().contains(queryLower)
            }
            .take(15)
        results.addAll(livesets.map { livesetMediaItem(it, showEditionInfo = true) })

        // Search editions from cache
        val editions = editionRepository.cachedEditions.value
            .filter { ed ->
                ed.edition.tagLine?.lowercase()?.contains(queryLower) == true ||
                ed.edition.number.lowercase().contains(queryLower)
            }
            .take(5)
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