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

    // Track cast status
    private val _isCasting = MutableStateFlow(false)
    val isCasting = _isCasting.asStateFlow()

    private var castPlayer: CastPlayer? = null
    private var castContext: CastContext? = null

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
        val newCastPlayer = CastPlayer(ctx, DefaultMediaItemConverter())
        transferPlaybackState(old, newCastPlayer)
        _activePlayer.value = newCastPlayer
        castPlayer = newCastPlayer
        _isCasting.value = true
        Log.d(TAG, "Switched to CastPlayer successfully")
        // Pause the old local player
        (old as? ExoPlayer)?.let { exo ->
            runCatching { exo.pause() }
        }
    }

    @OptIn(UnstableApi::class)
    private fun switchToLocal() {
        Log.d(TAG, "Switching back to local player...")
        val oldCast = castPlayer
        val local = when (val p = _activePlayer.value) {
            is ExoPlayer -> p
            else -> playerFactory.create(currentBufferMinutes).also { _activePlayer.value = it }
        }
        if (oldCast != null) {
            transferPlaybackState(oldCast, local)
            runCatching { oldCast.release() }.onFailure {
                Log.w(TAG, "Failed to release CastPlayer", it)
            }
        }
        _activePlayer.value = local
        castPlayer = null
        _isCasting.value = false
        Log.d(TAG, "Switched to local ExoPlayer successfully")
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

                to.setMediaItems(items, index, position)
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
