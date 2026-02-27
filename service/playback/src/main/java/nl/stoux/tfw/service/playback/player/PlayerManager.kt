package nl.stoux.tfw.service.playback.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import nl.stoux.tfw.service.playback.di.PlayerFactory
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PlayerManager"

/**
 * Callback for when playback needs to be rebuilt after switching from Cast to local.
 * Called with the liveset ID and position to resume from.
 */
fun interface CastToLocalCallback {
    suspend fun onSwitchingToLocal(livesetId: Long?, positionMs: Long)
}

/**
 * Callback for when switching from local to Cast.
 * Called with the liveset ID and position to rebuild the queue with Cast-compatible quality.
 */
fun interface LocalToCastCallback {
    suspend fun onSwitchingToCast(livesetId: Long?, positionMs: Long)
}

/**
 * Minimal PlayerManager for MVP: wraps a local ExoPlayer and exposes it as a StateFlow<Player>.
 * This sets the foundation for later Cast integration without changing Service code.
 */
@Singleton
@OptIn(UnstableApi::class)
class PlayerManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val playerFactory: PlayerFactory,
    private val playbackSettings: PlaybackSettingsRepository,
) {

    private val _activePlayer = MutableStateFlow<Player?>(null)
    val activePlayer = _activePlayer.asStateFlow()

    /**
     * Callback to notify when switching from Cast to local player.
     * Set by QueueManager to rebuild the queue properly.
     */
    var castToLocalCallback: CastToLocalCallback? = null

    /**
     * Callback to notify when switching from local to Cast player.
     * Set by QueueManager to rebuild the queue with Cast-compatible quality (lossless/WAV).
     */
    var localToCastCallback: LocalToCastCallback? = null

    // Track cast status
    private val _isCasting = MutableStateFlow(false)
    val isCasting = _isCasting.asStateFlow()

    private var castPlayer: CastPlayer? = null
    private var castContext: CastContext? = null

    // Track position when switching to Cast (fallback if Cast returns 0 on disconnect)
    private var positionBeforeCast: Long = 0L
    private var livesetIdBeforeCast: Long? = null

    // Current buffer duration in minutes for player creation
    private var currentBufferMinutes: Int = PlaybackSettingsRepository.DEFAULT_BUFFER_MINUTES

    // Coroutine scope for settings observation
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // Observe buffer duration changes and recreate player when needed
        scope.launch {
            playbackSettings.bufferDurationMinutes()
                .distinctUntilChanged()
                .collect { newMinutes ->
                    val oldMinutes = currentBufferMinutes
                    currentBufferMinutes = newMinutes

                    // Only recreate if we have an active local player and duration changed
                    val currentPlayer = _activePlayer.value
                    if (currentPlayer is ExoPlayer && oldMinutes != newMinutes) {
                        Log.d(TAG, "Buffer duration changed from ${oldMinutes}m to ${newMinutes}m, recreating player")
                        recreateLocalPlayer()
                    }
                }
        }
        // Initialize Cast context if available and listen for session changes.
        runCatching {
            castContext = CastContext.getSharedInstance(appContext)
            castContext?.sessionManager?.addSessionManagerListener(
                object : SessionManagerListener<CastSession> {
                    override fun onSessionStarted(session: CastSession, sessionId: String) {
                        switchToCast(session)
                    }
                    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                        switchToCast(session)
                    }
                    override fun onSessionEnding(session: CastSession) {}
                    override fun onSessionSuspended(session: CastSession, reason: Int) {}
                    override fun onSessionEnded(session: CastSession, error: Int) {
                        switchToLocal()
                    }
                    override fun onSessionStarting(session: CastSession) {}
                    override fun onSessionResuming(session: CastSession, sessionId: String) {}
                    override fun onSessionStartFailed(session: CastSession, error: Int) {}
                    override fun onSessionResumeFailed(session: CastSession, error: Int) {}
                },
                CastSession::class.java
            )
        }.onFailure {
            // Cast framework not available; ignore.
        }
    }

    @OptIn(UnstableApi::class)
    @Synchronized
    fun currentPlayer(): Player {
        val player = _activePlayer.value
        if (player != null) return player

        val newPlayer = playerFactory.create(currentBufferMinutes)
        _activePlayer.value = newPlayer
        return newPlayer
    }

    /**
     * Recreates the local ExoPlayer with current settings, preserving playback state.
     */
    @OptIn(UnstableApi::class)
    @Synchronized
    private fun recreateLocalPlayer() {
        val oldPlayer = _activePlayer.value as? ExoPlayer ?: return
        val newPlayer = playerFactory.create(currentBufferMinutes)
        transferPlaybackState(oldPlayer, newPlayer)
        _activePlayer.value = newPlayer
        runCatching { oldPlayer.release() }.onFailure {
            Log.w(TAG, "Failed to release old player during recreation", it)
        }
        Log.d(TAG, "Player recreated with ${currentBufferMinutes}m buffer")
    }

    @OptIn(UnstableApi::class)
    private fun switchToCast(session: CastSession) {
        Log.d(TAG, "Switching to Cast player...")
        val old = _activePlayer.value ?: playerFactory.create(currentBufferMinutes).also { _activePlayer.value = it }
        val ctx = castContext ?: run {
            Log.e(TAG, "switchToCast: CastContext is null, cannot switch")
            return
        }

        // Save position before switching to Cast (fallback if Cast returns 0 on disconnect)
        var currentLivesetId: Long? = null
        var currentPositionMs: Long = 0L
        runCatching {
            currentPositionMs = old.currentPosition.coerceAtLeast(0L)
            positionBeforeCast = currentPositionMs
            val mediaId = old.currentMediaItem?.mediaId
            if (mediaId != null && mediaId.startsWith("liveset:")) {
                val idPart = mediaId.removePrefix("liveset:").split("@").firstOrNull()
                currentLivesetId = idPart?.toLongOrNull()
                livesetIdBeforeCast = currentLivesetId
            }
            Log.d(TAG, "Saved position before Cast: livesetId=$livesetIdBeforeCast, position=${positionBeforeCast}ms")
        }

        val newCastPlayer = CastPlayer(ctx, DefaultMediaItemConverter())
        _activePlayer.value = newCastPlayer
        castPlayer = newCastPlayer
        _isCasting.value = true

        // Pause the old local player
        (old as? ExoPlayer)?.let { exo ->
            runCatching { exo.pause() }
        }

        // Use callback to rebuild queue with Cast-compatible quality (lossless/WAV)
        val callback = localToCastCallback
        if (callback != null && currentLivesetId != null) {
            Log.d(TAG, "Using callback to rebuild queue for Cast with lossless: liveset=$currentLivesetId at position=${currentPositionMs}ms")
            scope.launch {
                callback.onSwitchingToCast(currentLivesetId, currentPositionMs)
            }
        } else {
            // Fallback: transfer existing items (may fail if format not supported)
            Log.d(TAG, "No callback, falling back to direct transfer")
            transferPlaybackState(old, newCastPlayer)
        }
        Log.d(TAG, "Switched to CastPlayer successfully")
    }

    @OptIn(UnstableApi::class)
    private fun switchToLocal() {
        Log.d(TAG, "Switching back to local player...")
        val oldCast = castPlayer

        // Extract current liveset ID and position from Cast before switching
        // CastPlayer media items don't have localConfiguration.uri, only mediaId
        var currentLivesetId: Long? = null
        var currentPositionMs: Long = 0L
        if (oldCast != null) {
            runCatching {
                val mediaId = oldCast.currentMediaItem?.mediaId
                currentPositionMs = oldCast.currentPosition.coerceAtLeast(0L)
                // Parse liveset ID from mediaId format: "liveset:<id>" or "liveset:<id>@<timestamp>"
                if (mediaId != null && mediaId.startsWith("liveset:")) {
                    val idPart = mediaId.removePrefix("liveset:").split("@").firstOrNull()
                    currentLivesetId = idPart?.toLongOrNull()
                }
                Log.d(TAG, "Cast state: livesetId=$currentLivesetId, position=${currentPositionMs}ms")

                // If Cast position is 0 but we have a saved position, use the fallback
                // This handles the case where Cast disconnects before playback actually started
                if (currentPositionMs == 0L && positionBeforeCast > 0L) {
                    Log.d(TAG, "Cast position is 0, using fallback: livesetId=$livesetIdBeforeCast, position=${positionBeforeCast}ms")
                    currentPositionMs = positionBeforeCast
                    if (currentLivesetId == null) {
                        currentLivesetId = livesetIdBeforeCast
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to extract Cast playback state", e)
            }
        }

        // Clear the saved position after using it
        positionBeforeCast = 0L
        livesetIdBeforeCast = null

        val local = when (val p = _activePlayer.value) {
            is ExoPlayer -> p
            else -> playerFactory.create(currentBufferMinutes).also { _activePlayer.value = it }
        }

        // Release the cast player
        if (oldCast != null) {
            runCatching { oldCast.release() }.onFailure {
                Log.w(TAG, "Failed to release CastPlayer", it)
            }
        }

        _activePlayer.value = local
        castPlayer = null
        _isCasting.value = false

        // Use the callback to properly rebuild the queue with resolved URIs
        val callback = castToLocalCallback
        if (callback != null && currentLivesetId != null) {
            Log.d(TAG, "Using callback to rebuild queue for liveset=$currentLivesetId at position=${currentPositionMs}ms")
            scope.launch {
                callback.onSwitchingToLocal(currentLivesetId, currentPositionMs)
            }
        } else {
            Log.d(TAG, "Switched to local ExoPlayer (no callback or no liveset to resume)")
        }
    }

    @OptIn(UnstableApi::class)
    private fun transferPlaybackState(from: Player, to: Player) {
        val fromType = from.javaClass.simpleName
        val toType = to.javaClass.simpleName
        Log.d(TAG, "Transferring playback state from $fromType to $toType")

        runCatching {
            val itemCount = from.mediaItemCount
            if (itemCount > 0) {
                val items = (0 until itemCount).map { from.getMediaItemAt(it) }
                val index = from.currentMediaItemIndex.coerceIn(0, items.size - 1)
                val position = from.currentPosition.coerceAtLeast(0L)
                val playWhenReady = from.playWhenReady

                Log.d(TAG, "Transfer: $itemCount items, index=$index, position=${position}ms, playWhenReady=$playWhenReady")

                // Log first item details for debugging
                items.firstOrNull()?.let { item ->
                    Log.d(TAG, "First item: mediaId=${item.mediaId}, uri=${item.localConfiguration?.uri}, mimeType=${item.localConfiguration?.mimeType}")
                }

                // Filter to only items with valid URIs (CastPlayer items may not have localConfiguration)
                val validItems = items.filter { it.localConfiguration?.uri != null }
                if (validItems.isEmpty()) {
                    Log.w(TAG, "No valid items to transfer (all items missing URI). Skipping transfer - queue will need to be rebuilt.")
                    return@runCatching
                }

                // Adjust index if items were filtered out
                val adjustedIndex = index.coerceIn(0, validItems.size - 1)
                Log.d(TAG, "After filtering: ${validItems.size} valid items, adjustedIndex=$adjustedIndex")

                to.setMediaItems(validItems, adjustedIndex, position)
                to.prepare()
                to.playWhenReady = playWhenReady
                Log.d(TAG, "Transfer completed successfully")
            } else {
                Log.d(TAG, "No items to transfer")
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to transfer playback state from $fromType to $toType", e)
        }
    }

    fun release() {
        val player = _activePlayer.value as? ExoPlayer ?: return

        runCatching {
            player.playWhenReady = false
            player.stop()
            player.clearMediaItems()
        }

        player.release()
        _activePlayer.value = null
    }

}
