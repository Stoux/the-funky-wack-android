package nl.stoux.tfw.service.playback.service

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.stoux.tfw.service.playback.player.PlayerManager
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import nl.stoux.tfw.service.playback.service.session.CustomMediaId
import nl.stoux.tfw.service.playback.service.session.LibraryManager

@AndroidEntryPoint
class MediaPlaybackService : MediaLibraryService() {

    // De shit https://stackoverflow.com/questions/76838126/can-i-define-a-medialibraryservice-without-an-app

    @Inject lateinit var playerManager: PlayerManager

    @Inject lateinit var libraryManager: LibraryManager

    private var mediaLibrarySession: MediaLibrarySession? = null

    private val serviceIOScope = CoroutineScope(Dispatchers.IO)
    private val serviceMainScope = CoroutineScope(Dispatchers.Main)

    private val commandPreviousTrack = SessionCommand("track::previous", Bundle.EMPTY)
    private val commandNextTrack = SessionCommand("track::next", Bundle.EMPTY)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.d("MediaPlaybackService", "onCreate()")
        val player = playerManager.currentPlayer()

        // In a shared constants file or inside your service
        val previousTrackButton = CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
            .setDisplayName("Skip to previous track")
            .setSessionCommand(commandPreviousTrack)
            .build()

        val nextTrackButton = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
            .setDisplayName("Skip to next track")
            .setSessionCommand(commandNextTrack)
            .build()

        // Build the session
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, SessionCallback())
            .setId("tfw-media-session")
            .setCustomLayout(listOf(previousTrackButton, nextTrackButton))
            .build()

        // Add listener


        // When service starts: refresh the editions & notify the session that it's root options have changed
        serviceIOScope.launch {
            libraryManager.init()
            serviceMainScope.launch {
                mediaLibrarySession?.notifyChildrenChanged("root", 2, null)
            }
        }
    }


    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        Log.d("MediaPlaybackService", "onDestroy()")

        mediaLibrarySession?.release()
        mediaLibrarySession = null
        playerManager.release()
        super.onDestroy()
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
                Log.d("Commands", "Toglge Skip Mode Called")
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
            Log.d("OnGetItem", mediaId)
            return super.onGetItem(session, browser, mediaId)
        }

        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<List<MediaItem>> {

            // Check if it was a track action
            val firstItem = mediaItems.firstOrNull()
            if (firstItem?.mediaId?.contains("@") ?: false) {
                Log.d("Action", "We should skip to timestamp! ${firstItem.mediaId}")

                val player = playerManager.currentPlayer()
                val seekTo = firstItem.mediaId.substringAfter("@").toLongOrNull()

                player.seekTo((seekTo ?: 10 ) * 1000)

                val mediaItem = player.currentMediaItem
                if (mediaItem != null) {
                    val meta = mediaItem.mediaMetadata.buildUpon()
                        .setTitle("New track baby")
                        .build()
                    player.replaceMediaItem(player.currentMediaItemIndex, mediaItem.buildUpon().setMediaMetadata(meta).build())
                }


                // Return the player's CURRENT playlist to avoid any changes.
                return Futures.immediateFuture(null)
            }

            val items = mediaItems.mapNotNull { item -> resolvePlayableMediaItem(CustomMediaId.from(item.mediaId)) }

            Log.d("MediaPLaybackService", "Media items added (${mediaItems.size}: ${mediaItems.joinToString { it -> it.mediaId }}")

            return Futures.immediateFuture(items)
        }

        private fun resolvePlayableMediaItem( mediaId: CustomMediaId ): MediaItem? {
            // We only support playing
            val livesetId = mediaId.getLivesetId()
            if (livesetId == null) return null

            return libraryManager.livesetMediaItem(livesetId)
        }


    }
}
