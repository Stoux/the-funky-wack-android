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
 * Coordinator that manages the last playing state of the service.
 * - Saves last played mediaId + position when playback stops/pauses
 * - Restores the last played item on service start
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

        // Build a listener for this specific player
        val listenerForPlayer = object : Player.Listener {
            // Save state on pause/stop
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    scope.launch {
                        resolvePlayerStateAndStore(player)
                    }
                }
            }
        }

        // Store & attach it so we can unbind later
        listener = listenerForPlayer
        player.addListener(listenerForPlayer)
    }

    suspend fun resolvePlayerStateAndStore(player: Player) {
        val state = withContext(Dispatchers.Main.immediate) {
            val current: MediaItem? = player.currentMediaItem
            val id = current?.mediaId
            val pos = player.currentPosition
            if (id == null) null else {
                PlaybackStateRepository.State(id, pos)
            }
        }
        if (state != null) {
            // Persist on IO
            playbackStateRepository.saveState(state)
            Log.d("PlaybackResume", "Saved state: ${state.mediaId}@${state.positionMs}")
        }
    }

    suspend fun detach(player: Player) {
        // Detach our player
        withContext(Dispatchers.Main) {
            listener?.let { player.removeListener(it) }
            listener = null
        }

        // Save the state one last time
        withContext(Dispatchers.IO) {
            resolvePlayerStateAndStore(player)
        }
    }

    private suspend fun tryRestore(player: Player) {
        // Check if there's any last state to go back to
        val last = playbackStateRepository.lastState().firstOrNull() ?: return
        if (last.mediaId.isEmpty()) return

        try {
            // Parse the last media ID & make sure it's a (valid) liveset ID
            val mediaId = CustomMediaId.from(last.mediaId)
            val item: MediaItem? = when {
                mediaId.isLiveset() -> libraryManager.livesetMediaItem(mediaId.getLivesetId()!!)
                else -> return
            }

            // Invalid.
            if (item == null) return

            // Prepare player to the last item and seek; do NOT autoplay
            withContext(Dispatchers.Main) {
                // Don't play it if something else ready was found (i.e. direct link)
                if (player.currentMediaItem != null) {
                    return@withContext
                }

                // Load that media item & prepare the loader (but don't start it)
                player.setMediaItem(item, last.positionMs)
                player.prepare()

                Log.d("PlaybackResume", "Restored state: ${last.mediaId}@${last.positionMs}}")
            }
        } catch (t: Throwable) {
            Log.e("PlaybackResume", "Failed to restore state", t)
        }
    }
}