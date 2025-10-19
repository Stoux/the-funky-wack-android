package nl.stoux.tfw.service.playback.player

import android.content.Context
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
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.stoux.tfw.service.playback.di.PlayerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal PlayerManager for MVP: wraps a local ExoPlayer and exposes it as a StateFlow<Player>.
 * This sets the foundation for later Cast integration without changing Service code.
 */
@Singleton
@OptIn(UnstableApi::class)
class PlayerManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val playerFactory: PlayerFactory
) {

    private val _activePlayer = MutableStateFlow<Player?>(null)
    val activePlayer = _activePlayer.asStateFlow()

    // Track cast status
    private val _isCasting = MutableStateFlow(false)
    val isCasting = _isCasting.asStateFlow()

    private var castPlayer: CastPlayer? = null
    private var castContext: CastContext? = null

    init {
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

        val newPlayer = playerFactory.create()
        _activePlayer.value = newPlayer
        return newPlayer
    }

    @OptIn(UnstableApi::class)
    private fun switchToCast(session: CastSession) {
        val old = _activePlayer.value ?: playerFactory.create().also { _activePlayer.value = it }
        val ctx = castContext ?: return
        val newCastPlayer = CastPlayer(ctx, DefaultMediaItemConverter())
        transferPlaybackState(old, newCastPlayer)
        _activePlayer.value = newCastPlayer
        castPlayer = newCastPlayer
        _isCasting.value = true
        // Pause the old local player
        (old as? ExoPlayer)?.let { exo ->
            runCatching { exo.pause() }
        }
    }

    @OptIn(UnstableApi::class)
    private fun switchToLocal() {
        val oldCast = castPlayer
        val local = when (val p = _activePlayer.value) {
            is ExoPlayer -> p
            else -> playerFactory.create().also { _activePlayer.value = it }
        }
        if (oldCast != null) {
            transferPlaybackState(oldCast, local)
            runCatching { oldCast.release() }
        }
        _activePlayer.value = local
        castPlayer = null
        _isCasting.value = false
    }

    @OptIn(UnstableApi::class)
    private fun transferPlaybackState(from: Player, to: Player) {
        runCatching {
            val itemCount = from.mediaItemCount
            if (itemCount > 0) {
                val items = (0 until itemCount).map { from.getMediaItemAt(it) }
                val index = from.currentMediaItemIndex.coerceIn(0, items.size - 1)
                val position = from.currentPosition.coerceAtLeast(0L)
                val playWhenReady = from.playWhenReady
                to.setMediaItems(items, index, position)
                to.prepare()
                to.playWhenReady = playWhenReady
            }
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
