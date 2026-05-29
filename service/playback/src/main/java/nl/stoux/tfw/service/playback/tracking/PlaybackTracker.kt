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
import nl.stoux.tfw.core.common.device.DeviceIdManager
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.auth.PlaybackEventRequest
import nl.stoux.tfw.core.common.network.dto.auth.PlaybackStartRequest
import retrofit2.HttpException
import nl.stoux.tfw.service.playback.service.session.CustomMediaId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports playback events to the backend so plays, seek history, and
 * "continue listening" positions get attributed to the right user/device.
 *
 * Per the API reference, the Android app uses the HTTP `/api/playback/track`
 * endpoint for every event (start/progress/seek/pause/resume/stop/quality).
 * WebSocket whispers are a web-only path.
 */
@Singleton
class PlaybackTracker @Inject constructor(
    private val api: ApiService,
    private val authRepository: AuthRepository,
    private val deviceIdManager: DeviceIdManager,
) {
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private var listener: Player.Listener? = null
    private var attachedPlayer: Player? = null
    private var currentSessionId: Long? = null
    private var currentLivesetId: Long? = null
    private var progressJob: Job? = null
    private var listenedSecondsAccumulator = 0L
    private var lastPlayingPositionSec: Long = 0L

    fun attach(player: Player) {
        // Detach from previous player if any (Cast switch, etc.) so we don't
        // leave a stale Player.Listener bound on the old instance.
        attachedPlayer?.let { prev -> listener?.let { prev.removeListener(it) } }
        attachedPlayer = player
        val trackingListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val livesetId = mediaItem?.mediaId?.let { CustomMediaId.from(it).getLivesetId() }
                if (livesetId != null && livesetId != currentLivesetId) {
                    stopTracking(player)
                    startTracking(livesetId, player)
                } else if (livesetId == null) {
                    stopTracking(player)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    val livesetId = player.currentMediaItem?.mediaId?.let { CustomMediaId.from(it).getLivesetId() }
                    if (livesetId != null && currentSessionId == null) {
                        startTracking(livesetId, player)
                    } else {
                        sendEvent("resume", player)
                    }
                    startProgressReporting(player)
                } else {
                    progressJob?.cancel()
                    progressJob = null
                    sendEvent("pause", player)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    val sessionId = currentSessionId ?: return
                    val fromSec = (oldPosition.positionMs / 1000).coerceAtLeast(0L)
                    val toSec = (newPosition.positionMs / 1000).coerceAtLeast(0L)
                    scope.launch {
                        runCatching {
                            api.trackPlaybackEvent(
                                PlaybackEventRequest(
                                    event = "seek",
                                    playHistoryId = sessionId,
                                    position = toSec,
                                    fromPosition = fromSec,
                                )
                            )
                        }.onFailure { logEventFailure("seek", it) }
                    }
                }
            }
        }

        listener = trackingListener
        player.addListener(trackingListener)
    }

    fun detach(player: Player) {
        sendEvent("stop", player)
        listener?.let { player.removeListener(it) }
        listener = null
        attachedPlayer = null
        stopTracking(player)
    }

    private fun startTracking(livesetId: Long, player: Player) {
        if (!authRepository.isLoggedIn.value) return
        currentLivesetId = livesetId
        listenedSecondsAccumulator = 0L

        scope.launch {
            try {
                val rawPositionMs = withContext(Dispatchers.Main) { player.currentPosition }
                // Player can return Long.MIN_VALUE (C.TIME_UNSET) before prepared,
                // and the server validates position >= 0 — clamp before sending.
                val positionSec = (rawPositionMs / 1000).coerceAtLeast(0L)
                lastPlayingPositionSec = positionSec
                val response = api.startPlaybackSession(
                    PlaybackStartRequest(
                        livesetId = livesetId,
                        event = "start",
                        position = positionSec,
                        platform = "android",
                        clientId = deviceIdManager.clientId,
                    )
                )
                currentSessionId = response.playHistoryId
                Log.d(TAG, "Started session ${response.playHistoryId} for liveset $livesetId")
            } catch (e: HttpException) {
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                Log.w(TAG, "Failed to start playback session: HTTP ${e.code()} body=$body")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start playback session", e)
            }
        }
    }

    private fun stopTracking(player: Player) {
        progressJob?.cancel()
        progressJob = null
        currentSessionId = null
        currentLivesetId = null
        listenedSecondsAccumulator = 0L
    }

    private fun startProgressReporting(player: Player) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                delay(15_000)
                val sessionId = currentSessionId ?: continue
                val positionSec = withContext(Dispatchers.Main) { player.currentPosition / 1000 }
                listenedSecondsAccumulator += 15
                runCatching {
                    api.trackPlaybackEvent(
                        PlaybackEventRequest(
                            event = "progress",
                            playHistoryId = sessionId,
                            position = positionSec.coerceAtLeast(0L),
                            durationListened = listenedSecondsAccumulator,
                        )
                    )
                }.onFailure { logEventFailure("progress", it) }
            }
        }
    }

    private fun sendEvent(event: String, player: Player) {
        val sessionId = currentSessionId ?: return
        scope.launch {
            val positionSec = withContext(Dispatchers.Main) { player.currentPosition / 1000 }
            runCatching {
                api.trackPlaybackEvent(
                    PlaybackEventRequest(
                        event = event,
                        playHistoryId = sessionId,
                        position = positionSec.coerceAtLeast(0L),
                        durationListened = if (event == "stop") listenedSecondsAccumulator else null,
                    )
                )
            }.onFailure { logEventFailure(event, it) }
        }
    }

    private fun logEventFailure(event: String, t: Throwable) {
        if (t is HttpException) {
            val body = runCatching { t.response()?.errorBody()?.string() }.getOrNull()
            Log.w(TAG, "$event event failed: HTTP ${t.code()} body=$body")
        } else {
            Log.w(TAG, "$event event failed", t)
        }
    }

    fun release() {
        // Best-effort final stop event if we're still mid-session
        attachedPlayer?.let { sendEvent("stop", it) }
        progressJob?.cancel()
        progressJob = null
        currentSessionId = null
        currentLivesetId = null
        attachedPlayer = null
    }

    companion object {
        private const val TAG = "PlaybackTracker"
    }
}
