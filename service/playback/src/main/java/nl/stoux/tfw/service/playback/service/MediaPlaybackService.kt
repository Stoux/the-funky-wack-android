package nl.stoux.tfw.service.playback.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.app.UiModeManager
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import nl.stoux.tfw.service.playback.download.DownloadTracker
import nl.stoux.tfw.service.playback.player.PlayerManager
import nl.stoux.tfw.service.playback.tracking.PlaybackTracker
import nl.stoux.tfw.service.playback.service.manager.LivesetTrackListener
import nl.stoux.tfw.service.playback.service.manager.LivesetTrackManager
import nl.stoux.tfw.service.playback.service.manager.NotificationUpdateCallback
import nl.stoux.tfw.service.playback.service.manager.UnbindCallback
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import nl.stoux.tfw.service.playback.service.resume.PlaybackResumeCoordinator
import nl.stoux.tfw.service.playback.service.resume.PlaybackStateRepository
import nl.stoux.tfw.service.playback.service.session.CustomMediaId
import nl.stoux.tfw.service.playback.service.session.LibraryChildrenChangedCallback
import nl.stoux.tfw.service.playback.service.session.LibraryManager
import javax.inject.Inject


@AndroidEntryPoint
class MediaPlaybackService : MediaLibraryService() {

    // De shit https://stackoverflow.com/questions/76838126/can-i-define-a-medialibraryservice-without-an-app

    @Inject lateinit var playerManager: PlayerManager

    @Inject lateinit var libraryManager: LibraryManager

    @Inject lateinit var trackManager: LivesetTrackManager

    @Inject lateinit var resumeCoordinator: PlaybackResumeCoordinator

    @Inject lateinit var playbackStateRepository: PlaybackStateRepository

    @Inject lateinit var queueManager: QueueManager

    @Inject lateinit var downloadTracker: DownloadTracker

    @Inject lateinit var playbackTracker: PlaybackTracker

    @Inject lateinit var listenAlongCoordinator: nl.stoux.tfw.service.playback.live.ListenAlongCoordinator

    @Inject lateinit var reverbClient: nl.stoux.tfw.core.common.network.ws.ReverbClient

    private var mediaLibrarySession: MediaLibrarySession? = null

    private val serviceIOScope = CoroutineScope(Dispatchers.IO)
    private val serviceMainScope = CoroutineScope(Dispatchers.Main)

    private var trackManagerUnbindCallback: UnbindCallback? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.d("MediaPlaybackService", "onCreate()")
        val player = playerManager.currentPlayer()

        // Prepare session activity PendingIntent to open the app and show the player (disabled on Automotive)
        val sessionActivityPendingIntent = if (!isAutomotiveDevice()) {
            val baseIntent = packageManager?.getLaunchIntentForPackage(packageName)
            val sessionActivityIntent = baseIntent?.let {
                Intent(it).apply {
                    action = ACTION_SHOW_PLAYER
                    // Ensure the existing activity is reused if running
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
            sessionActivityIntent?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
            }
        } else {
            // On Automotive: do not set a session activity. Let the system Media Center handle the UI.
            null
        }

        // Build the session with a BitmapLoader for remote artwork (needed for Automotive album art)
        val bitmapLoader = CacheBitmapLoader(DataSourceBitmapLoader(this))
        val sessionBuilder = MediaLibrarySession.Builder(this, player, SessionCallback())
            .setId("tfw-media-session")
            .setCustomLayout(buildCustomLayout(false, false))
            .setBitmapLoader(bitmapLoader)
        if (sessionActivityPendingIntent != null) {
            sessionBuilder.setSessionActivity(sessionActivityPendingIntent)
        }
        mediaLibrarySession = sessionBuilder.build()

        // Attach resume/persist coordinator (restores last state and starts listening)
        resumeCoordinator.attach(player)

        // Attach playback tracker for server-side play counting
        playbackTracker.attach(player)

        // Add listener
        trackManagerUnbindCallback = trackManager.bind(TrackListener())

        // Wire up library children changed callback for refresh status updates
        libraryManager.childrenChangedCallback = LibraryChildrenChangedCallback { parentId, itemCount ->
            serviceMainScope.launch {
                mediaLibrarySession?.notifyChildrenChanged(parentId, itemCount, null)
            }
        }

        // Wire up notification update callback for track metadata changes
        // replaceMediaItem() doesn't automatically notify all MediaSession controllers,
        // so we broadcast a custom command to wake them up (fixes Android Auto not updating)
        trackManager.notificationUpdateCallback = NotificationUpdateCallback {
            serviceMainScope.launch {
                // Broadcast custom command to wake up all controllers (Android Auto)
                // This triggers controllers to re-read the player's current metadata
                mediaLibrarySession?.broadcastCustomCommand(
                    SessionCommand("metadata_changed", Bundle.EMPTY),
                    Bundle.EMPTY
                )
            }
        }

        // When service starts: refresh the editions & notify the session that its root options have changed
        serviceIOScope.launch {
            libraryManager.init()
            serviceMainScope.launch {
                mediaLibrarySession?.notifyChildrenChanged(CustomMediaId.ROOT.original, libraryManager.getRootChildrenCount(), null)
            }
        }

        // Change the underlying player if we switch (i.e. when casting).
        var lastAttachedForResume: androidx.media3.common.Player? = player
        serviceMainScope.launch {
            playerManager.activePlayer.collect { activePlayer ->
                if (activePlayer != null) {
                    mediaLibrarySession?.player = activePlayer
                    if (lastAttachedForResume !== activePlayer) {
                        // Re-attach resume coordinator to the new player
                        serviceIOScope.launch {
                            lastAttachedForResume?.let { runCatching { resumeCoordinator.detach(it) } }
                            runCatching { resumeCoordinator.attach(activePlayer) }
                            lastAttachedForResume = activePlayer
                        }
                    }
                }
            }
        }
    }

    fun buildCustomLayout(hasPreviousTrack: Boolean, hasNextTrack: Boolean): List<CommandButton> {
        // In a shared constants file or inside your service
        val previousTrackButton = CommandButton.Builder(CommandButton.ICON_REWIND)
            .setDisplayName("Skip to previous track")
            .setSessionCommand(commandPreviousTrack)
            .setEnabled(hasPreviousTrack)
            .build()

        val nextTrackButton = CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
            .setDisplayName("Skip to next track")
            .setSessionCommand(commandNextTrack)
            .setEnabled(hasNextTrack)
            .build()

        return listOf(previousTrackButton, nextTrackButton);
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        Log.d("MediaPlaybackService", "onDestroy()")

        trackManagerUnbindCallback?.invoke()
        trackManagerUnbindCallback = null
        mediaLibrarySession?.release()
        mediaLibrarySession = null

        // Release singleton managers
        queueManager.release()
        trackManager.release()
        downloadTracker.release()
        playbackTracker.release()

        // Tear down listen-along membership + the Reverb websocket so the app
        // doesn't leave the user listed as an active listener after swipe-stop.
        // Block briefly here — the earlier fire-and-forget launch raced with
        // serviceIOScope.cancel() below and never reached the HTTP leave call.
        runCatching {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(2000) { listenAlongCoordinator.leave() }
            }
        }
        reverbClient.forceDisconnect()

        // Detach coordinator before releasing player (fire-and-forget, best-effort save)
        serviceIOScope.launch {
            runCatching { resumeCoordinator.detach(playerManager.currentPlayer()) }
        }
        // Release player on main thread
        playerManager.release()

        serviceIOScope.cancel()
        serviceMainScope.cancel()

        super.onDestroy()
    }

    /**
     * Method called when this service should be killed/stopped/removed.
     * Most important scenario is: swiping this app away from the recent apps should kill the media service.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Start a full cleanup (should call onDestroy eventually)
        Log.d("MediaPlaybackService", "calling stopSelf()")
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {

        /**
         * Any next/prev/seek issued at the Player layer by an EXTERNAL controller
         * (notification, BT remote, Auto/Automotive transport) while we're in a
         * synced listen-along room must be a user-initiated sync break — the
         * in-app PlayerViewModel paths intercept these and show the leave-sync
         * dialog before reaching the player. Our own in-app controller also
         * mirrors host events (Seek/Pause/Resume) here, so we filter those out
         * by package name. Fire a leave + let the command proceed.
         */
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int,
        ): Int {
            val isExternal = controller.packageName != packageName
            val syncBreaking = playerCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ||
                playerCommand == Player.COMMAND_SEEK_TO_NEXT ||
                playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS ||
                playerCommand == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM ||
                playerCommand == Player.COMMAND_SEEK_TO_MEDIA_ITEM
            if (isExternal && syncBreaking && listenAlongCoordinator.activeRoom.value?.mode == "synced") {
                serviceIOScope.launch { runCatching { listenAlongCoordinator.leave() } }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Auto-refresh if data is stale (e.g., car woke up from sleep), but not while playing
            if (!playerManager.currentPlayer().isPlaying) {
                serviceIOScope.launch {
                    val refreshed = libraryManager.refreshIfStale()
                    if (refreshed) {
                        serviceMainScope.launch {
                            mediaLibrarySession?.notifyChildrenChanged(CustomMediaId.ROOT.original, libraryManager.getRootChildrenCount(), null)
                        }
                    }
                }
            }

            val parent = super.onConnect(session, controller)
            val commands = parent.availableSessionCommands.buildUpon()
                .add(commandPreviousTrack)
                .add(commandNextTrack)
                .build()

            return MediaSession.ConnectionResult.accept(commands, MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
        }

        /**
         * Called by Android Automotive/Auto when the user presses play on the resumption card.
         * This is the proper hook for playback resumption - restores the last played liveset.
         * Uses QueueManager to ensure queue state is properly synchronized.
         */
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceIOScope.launch {
                try {
                    future.set(buildResumption())
                } catch (e: Exception) {
                    Log.e("MediaPlaybackService", "Failed to resume playback", e)
                    future.setException(e)
                }
            }
            return future
        }

        private suspend fun buildResumption(): MediaSession.MediaItemsWithStartPosition {
            val lastState = playbackStateRepository.lastState().firstOrNull()
            if (lastState == null || lastState.mediaId.isEmpty()) {
                throw IllegalStateException("No playback state to resume")
            }

            val livesetId = CustomMediaId.from(lastState.mediaId).getLivesetId()
                ?: throw IllegalStateException("Invalid media ID for resumption")

            // Build the queue via QueueManager to keep state in sync (includes manual queue)
            val (mediaItems, startIndex) = queueManager.buildContextForResumption(livesetId)
                ?: throw IllegalStateException("No playable items found for resumption")

            return MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, lastState.positionMs)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand == commandPreviousTrack) {
                trackManager.toPreviousTrack()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } else if (customCommand == commandNextTrack) {
                trackManager.toNextTrack()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = libraryManager.getRoot()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items = libraryManager.getChildren(parentId, page, pageSize)
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Only Livesets are supported to directly fetch
            val livesetId = CustomMediaId.from(mediaId).getLivesetId()
            if (livesetId != null) {
                val item = libraryManager.livesetMediaItem(livesetId)
                return Futures.immediateFuture(LibraryResult.ofItem(item, null));
            }

            return super.onGetItem(session, browser, mediaId)
        }

        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val firstItem = mediaItems.firstOrNull()

            // Voice search via addMediaItems: queue the top hit (a plain add can't carry a start position)
            val searchQuery = firstItem?.searchQueryOrNull()
            if (searchQuery != null) {
                return Futures.transform(
                    playFromSearch(searchQuery),
                    { it.mediaItems },
                    MoreExecutors.directExecutor(),
                )
            }

            // Track jump inside the set that's already loaded (liveset:<id>@<seconds>): just seek
            if (firstItem != null && isJumpWithinCurrentSet(firstItem)) {
                val player = playerManager.currentPlayer()
                val seekTo = firstItem.mediaId.substringAfter("@").toLongOrNull()
                if (seekTo != null) {
                    player.seekTo(seekTo * 1000)
                }

                // Return the player's CURRENT playlist to avoid any changes.
                return Futures.immediateCancelledFuture()
            }

            // Resolve the played item to a queue
            val items: List<MediaItem> = when {
                mediaItems.isNotEmpty() -> {
                    if (mediaItems.size > 1) {
                        Log.e("MediaPlaybackService", "Playback service received multiple Media items to play? Unsupported! IDs: ${mediaItems.joinToString { it.mediaId }}")
                    }
                    resolveQueue(mediaItems[0])
                }
                else -> emptyList()
            }

            return Futures.immediateFuture(items)
        }

        /**
         * Controllers (Android Auto / Automotive) set items without a start index, so with shuffle on
         * ExoPlayer would start at a random set. resolveQueue always puts the tapped liveset first.
         *
         * A `liveset:<id>@<seconds>` item for another set than the loaded one (search hits, Continue
         * listening) loads that set's queue and starts at the timestamp instead of seeking the current one.
         */
        @OptIn(UnstableApi::class)
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val first = mediaItems.firstOrNull()

            // "Play X on The Funky Wack" (playFromSearch): empty mediaId + a search query
            val searchQuery = first?.searchQueryOrNull()
            if (searchQuery != null) return playFromSearch(searchQuery)

            val customId = first?.let { CustomMediaId.fromOrNull(it.mediaId) }
            val timestampSec = customId?.timestamp
            if (first != null && customId?.isLiveset() == true && timestampSec != null && !isJumpWithinCurrentSet(first)) {
                val items = runCatching { resolveQueue(first) }.getOrElse { return Futures.immediateFailedFuture(it) }
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(items, 0, timestampSec * 1000L)
                )
            }

            return Futures.transform(
                onAddMediaItems(mediaSession, controller, mediaItems),
                { items -> MediaSession.MediaItemsWithStartPosition(items, 0, startPositionMs) },
                MoreExecutors.directExecutor(),
            )
        }

        /** Legacy play/prepareFromSearch arrive as an item with the default (empty) mediaId and a search query. */
        private fun MediaItem.searchQueryOrNull(): String? =
            requestMetadata.searchQuery?.takeIf { mediaId.isEmpty() }

        /** `liveset:<id>@<seconds>` for the set that's currently loaded in the player. */
        private fun isJumpWithinCurrentSet(item: MediaItem): Boolean {
            val customId = CustomMediaId.fromOrNull(item.mediaId) ?: return false
            if (customId.timestamp == null || !customId.isLiveset()) return false
            val currentLivesetId = mediaLibrarySession?.player?.currentMediaItem?.mediaId
                ?.let { CustomMediaId.fromOrNull(it)?.getLivesetId() }
            return currentLivesetId != null && currentLivesetId == customId.getLivesetId()
        }

        /**
         * Voice search: start the top backend hit (local match when offline) at its timestamp.
         * A blank query ("play The Funky Wack") resumes the last played set.
         */
        private fun playFromSearch(query: String): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceIOScope.launch {
                try {
                    if (query.isBlank()) {
                        future.set(buildResumption())
                        return@launch
                    }
                    val target = libraryManager.resolveSearchPlayTarget(query)
                        ?: throw IllegalStateException("No playable search hit for '$query'")
                    val items = libraryManager.getQueueBasedForLiveset(target.livesetId)
                    future.set(MediaSession.MediaItemsWithStartPosition(items, 0, target.startPositionMs))
                } catch (e: Exception) {
                    Log.w("MediaPlaybackService", "Voice search failed for '$query'", e)
                    future.setException(e)
                }
            }
            return future
        }

        private fun resolveQueue(item: MediaItem): List<MediaItem> {
            val customId = CustomMediaId.fromOrNull(item.mediaId) ?: return emptyList()
            val extras = item.mediaMetadata.extras
            val playlistShareCode = extras
                ?.getString(nl.stoux.tfw.service.playback.service.session.EXTRA_PLAYLIST_SHARE_CODE)
            val fromFavorites = extras
                ?.getBoolean(nl.stoux.tfw.service.playback.service.session.EXTRA_FROM_FAVORITES) == true
            return resolveQueue(customId, playlistShareCode, fromFavorites)
        }

        private fun resolveQueue(
            mediaId: CustomMediaId,
            playlistShareCode: String? = null,
            fromFavorites: Boolean = false,
        ): List<MediaItem> {
            // We only support playing
            val livesetId = mediaId.getLivesetId()
            if (livesetId == null) return emptyList()

            // Tap from inside a playlist shelf → queue is the playlist itself,
            // rotated so the tapped liveset is first. Favorites shelf works the
            // same way. Otherwise fall back to edition-context (the default).
            if (playlistShareCode != null) {
                return libraryManager.getQueueBasedForPlaylist(playlistShareCode, livesetId)
            }
            if (fromFavorites) {
                return libraryManager.getQueueBasedForFavorites(livesetId)
            }
            return libraryManager.getQueueBasedForLiveset(livesetId)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            // Accept right away, notify the browser with the count once the backend answered
            serviceIOScope.launch {
                val resultCount = runCatching { libraryManager.searchItems(query, 0, Int.MAX_VALUE).size }
                    .getOrElse {
                        Log.w("MediaPlaybackService", "Search failed for '$query'", it)
                        0
                    }
                serviceMainScope.launch {
                    session.notifySearchResultChanged(browser, query, resultCount, params)
                }
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceIOScope.launch {
                runCatching { libraryManager.searchItems(query, page, pageSize) }
                    .onSuccess { future.set(LibraryResult.ofItemList(ImmutableList.copyOf(it), params)) }
                    .onFailure {
                        Log.w("MediaPlaybackService", "Search results failed for '$query'", it)
                        future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN, params))
                    }
            }
            return future
        }

    }

    private inner class TrackListener: LivesetTrackListener{

        override fun onNextPrevTrackStatusChanged(
            hasPreviousTrack: Boolean,
            hasNextTrack: Boolean
        ) {
            mediaLibrarySession?.setCustomLayout(buildCustomLayout(hasPreviousTrack, hasNextTrack))
        }

    }

    private fun isAutomotiveDevice(): Boolean {
        val feature = packageManager?.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) == true
        val uiModeManager = getSystemService(UiModeManager::class.java)
        val isCarUiMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_CAR
        return feature || isCarUiMode
    }

    companion object {
        const val ACTION_SHOW_PLAYER = "nl.stoux.tfw.SHOW_PLAYER"
        val commandPreviousTrack = SessionCommand("track::previous", Bundle.EMPTY)
        val commandNextTrack = SessionCommand("track::next", Bundle.EMPTY)
    }
}
