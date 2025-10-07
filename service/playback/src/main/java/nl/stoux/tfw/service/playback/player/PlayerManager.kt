package nl.stoux.tfw.service.playback.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
class PlayerManager @Inject constructor(
    private val playerFactory: PlayerFactory
) {

    private val _activePlayer = MutableStateFlow<Player?>(null)
    val activePlayer = _activePlayer.asStateFlow()

    @OptIn(UnstableApi::class)
    @Synchronized
    fun currentPlayer(): Player {
        val player = _activePlayer.value
        if (player != null) return player

        val newPlayer = playerFactory.create()
        _activePlayer.value = newPlayer
        return newPlayer
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
