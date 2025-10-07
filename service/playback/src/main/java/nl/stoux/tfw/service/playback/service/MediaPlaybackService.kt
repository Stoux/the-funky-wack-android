package nl.stoux.tfw.service.playback.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nl.stoux.tfw.service.playback.player.PlayerManager
import nl.stoux.tfw.service.playback.service.manager.LivesetTrackListener
import nl.stoux.tfw.service.playback.service.manager.LivesetTrackManager
import nl.stoux.tfw.service.playback.service.manager.UnbindCallback
import nl.stoux.tfw.service.playback.service.resume.PlaybackResumeCoordinator
import nl.stoux.tfw.service.playback.service.session.CustomMediaId
import nl.stoux.tfw.service.playback.service.session.LibraryManager
import javax.inject.Inject


@AndroidEntryPoint
class MediaPlaybackService : MediaLibraryService() {

    // De shit https://stackoverflow.com/questions/76838126/can-i-define-a-medialibraryservice-without-an-app

    @Inject lateinit var playerManager: PlayerManager

    @Inject lateinit var libraryManager: LibraryManager

    @Inject lateinit var trackManager: LivesetTrackManager

    @Inject lateinit var resumeCoordinator: PlaybackResumeCoordinator

    private var mediaLibrarySession: MediaLibrarySession? = null

    private val serviceIOScope = CoroutineScope(Dispatchers.IO)
    private val serviceMainScope = CoroutineScope(Dispatchers.Main)

    private var trackManagerUnbindCallback: UnbindCallback? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.d("MediaPlaybackService", "onCreate()")
        val player = playerManager.currentPlayer()

        // Prepare session activity PendingIntent to open the app and show the player
        val baseIntent = packageManager?.getLaunchIntentForPackage(packageName)
        val sessionActivityIntent = baseIntent?.let {
            Intent(it).apply {
                action = ACTION_SHOW_PLAYER
                // Ensure the existing activity is reused if running
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        val sessionActivityPendingIntent = sessionActivityIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        // Build the session
        val sessionBuilder = MediaLibrarySession.Builder(this, player, SessionCallback())
            .setId("tfw-media-session")
            .setCustomLayout(buildCustomLayout(false, false))
        if (sessionActivityPendingIntent != null) {
            sessionBuilder.setSessionActivity(sessionActivityPendingIntent)
        }
        mediaLibrarySession = sessionBuilder.build()

        // Attach resume/persist coordinator (restores last state and starts listening)
        resumeCoordinator.attach(player)

        // Add listener
        trackManagerUnbindCallback = trackManager.bind(TrackListener())

        // When service starts: refresh the editions & notify the session that it's root options have changed
        serviceIOScope.launch {
            libraryManager.init()
            serviceMainScope.launch {
                mediaLibrarySession?.notifyChildrenChanged("root", 2, null)
            }
        }

        // Change the underlying player if we switch (i.e. when casting).
        serviceMainScope.launch {
            playerManager.activePlayer.collect { activePlayer ->
                if (activePlayer != null) {
                    mediaLibrarySession?.player = activePlayer
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

        // Detach coordinator before releasing player
        serviceIOScope.launch {
            runBlocking { resumeCoordinator.detach(playerManager.currentPlayer()) }
            serviceMainScope.launch {
                playerManager.release()

                serviceIOScope.cancel()
                serviceMainScope.cancel()
            }
        }

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

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val parent = super.onConnect(session, controller)
            val commands = parent.availableSessionCommands.buildUpon()
                .add(commandPreviousTrack)
                .add(commandNextTrack)
                .build()

            return MediaSession.ConnectionResult.accept(commands, parent.availablePlayerCommands)
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
            // Check if it was a track action encoded in the mediaId (legacy behavior: id@seconds)
            val firstItem = mediaItems.firstOrNull()
            if (firstItem?.mediaId?.contains("@") == true) {
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

                    resolveQueue(CustomMediaId.from(mediaItems[0].mediaId))
                }
                else -> emptyList()
            }

            return Futures.immediateFuture(items)
        }

        private fun resolveQueue( mediaId: CustomMediaId ): List<MediaItem> {
            // We only support playing
            val livesetId = mediaId.getLivesetId()
            if (livesetId == null) return emptyList()

            return libraryManager.getQueueBasedForLiveset(livesetId)
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

    companion object {
        const val ACTION_SHOW_PLAYER = "nl.stoux.tfw.SHOW_PLAYER"
        val commandPreviousTrack = SessionCommand("track::previous", Bundle.EMPTY)
        val commandNextTrack = SessionCommand("track::next", Bundle.EMPTY)
    }
}
