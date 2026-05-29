package nl.stoux.tfw.service.playback.tracking

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.auth.PlaybackStartRequest
import nl.stoux.tfw.service.playback.service.session.CustomMediaId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks playback events and reports them to the server.
 * Hooks into Media3 player events as a Player.Listener.
 *
 * Currently uses HTTP for session start. WebSocket whispers for progress/seek/pause/resume/stop
 * will be added when Pusher SDK is integrated.
 */
@Singleton
class PlaybackTracker @Inject constructor(
    private val api: ApiService,
    private val authRepository: AuthRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var listener: Player.Listener? = null
    private var currentSessionId: Long? = null
    private var currentLivesetId: Long? = null
    private var progressJob: Job? = null

    fun attach(player: Player) {
        val trackingListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val livesetId = mediaItem?.mediaId?.let { CustomMediaId.from(it).getLivesetId() }
                if (livesetId != null && livesetId != currentLivesetId) {
                    // Stop tracking old, start tracking new
                    stopTracking()
                    startTracking(livesetId, player)
                } else if (livesetId == null) {
                    stopTracking()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && currentLivesetId != null && currentSessionId == null) {
                    // Resumed or started playing — ensure we have a session
                    val id = player.currentMediaItem?.mediaId?.let { CustomMediaId.from(it).getLivesetId() }
                    if (id != null) startTracking(id, player)
                }
                // Start/stop periodic progress reporting
                if (isPlaying) {
                    startProgressReporting(player)
                } else {
                    progressJob?.cancel()
                    progressJob = null
                }
            }
        }

        listener = trackingListener
        player.addListener(trackingListener)
    }

    fun detach(player: Player) {
        // Send final stop event
        scope.launch {
            val position = withContext(Dispatchers.Main) { player.currentPosition }
            Log.d("PlaybackTracker", "Final stop at ${position / 1000}s (session: $currentSessionId)")
            // TODO: Send stop whisper via WebSocket when Pusher SDK is integrated
        }
        listener?.let { player.removeListener(it) }
        listener = null
        stopTracking()
    }

    private fun startTracking(livesetId: Long, player: Player) {
        if (!authRepository.isLoggedIn.value) return
        currentLivesetId = livesetId

        scope.launch {
            try {
                val position = withContext(Dispatchers.Main) { player.currentPosition }
                val response = api.startPlaybackSession(
                    PlaybackStartRequest(
                        livesetId = livesetId,
                        event = "start",
                        position = position / 1000,
                    )
                )
                currentSessionId = response.playHistoryId
                Log.d("PlaybackTracker", "Started session ${response.playHistoryId} for liveset $livesetId")
                // TODO: Join presence channel via Pusher when SDK is integrated
                // response.sessionChannel can be used for WebSocket whispers
            } catch (e: Exception) {
                Log.w("PlaybackTracker", "Failed to start playback session", e)
            }
        }
    }

    private fun stopTracking() {
        progressJob?.cancel()
        progressJob = null
        currentSessionId = null
        currentLivesetId = null
        // TODO: Send stop whisper via WebSocket when Pusher SDK is integrated
    }

    private fun startProgressReporting(player: Player) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                delay(15_000) // Report every 15 seconds
                // TODO: Send progress whisper via WebSocket when Pusher SDK is integrated
                // For now, this is a no-op placeholder
                val position = withContext(Dispatchers.Main) { player.currentPosition }
                Log.d("PlaybackTracker", "Progress: ${position / 1000}s (session: $currentSessionId)")
            }
        }
    }

    fun release() {
        stopTracking()
    }
}
