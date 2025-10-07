package nl.stoux.tfw.service.playback.service.resume

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.stoux.tfw.service.playback.service.session.CustomMediaId
import nl.stoux.tfw.service.playback.service.session.LibraryManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps MediaPlaybackService slim by handling resume/persist logic.
 * - Saves last played mediaId + position when playback stops/pauses (IO)
 * - Restores the last played item on service start (prepare on Main, data on IO)
 */
@Singleton
class PlaybackResumeCoordinator @Inject constructor(
    private val playbackStateRepository: PlaybackStateRepository,
    private val libraryManager: LibraryManager,
) : Player.Listener {

    // Use IO scope for persistence and loading; hop to Main when touching the Player
    private val scope = CoroutineScope(Dispatchers.IO)
    private var listener: Player.Listener? = null

    fun attach(player: Player) {
        // Restore last state on attach
        scope.launch {
            tryRestore(player)
        }

        // Save state on pause/stop
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    scope.launch {
                        // Read from player on Main thread
                        val (mediaId, position) = withContext(Dispatchers.Main) {
                            val current: MediaItem? = player.currentMediaItem
                            val id = current?.mediaId
                            val pos = player.currentPosition
                            id to pos
                        }
                        if (!mediaId.isNullOrEmpty()) {
                            // Persist on IO
                            playbackStateRepository.saveState(mediaId = mediaId, positionMs = position)
                            Log.d("PlaybackResume", "Saved state: ${'$'}mediaId@${'$'}position")
                        }
                    }
                }
            }
        }
        listener = l
        player.addListener(l)
    }

    fun detach(player: Player) {
        listener?.let { player.removeListener(it) }
        listener = null
    }

    private suspend fun tryRestore(player: Player) {
        // Check if there's any last state to go back to
        val last = playbackStateRepository.lastState().firstOrNull() ?: return
        if (last.mediaId.isEmpty()) return

        try {
            // Parse the last media ID & make sure it's a (valid) liveset ID
            val cmid = CustomMediaId.from(last.mediaId)
            val item: MediaItem? = when {
                cmid.isLiveset() -> libraryManager.livesetMediaItem(cmid.getLivesetId()!!)
                else -> return
            }

            // Invalid.
            if (item == null) return

            // Prepare player to the last item and seek; do NOT autoplay
            withContext(Dispatchers.Main) {
                player.setMediaItem(item, last.positionMs)
                player.prepare()
            }
            Log.d("PlaybackResume", "Restored state: ${'$'}{last.mediaId}@${'$'}{last.positionMs}")
        } catch (t: Throwable) {
            Log.e("PlaybackResume", "Failed to restore state", t)
        }
    }
}