package nl.stoux.tfw.service.playback.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal PlayerManager for MVP: wraps a local ExoPlayer and exposes it as a StateFlow<Player>.
 * This sets the foundation for later Cast integration without changing Service code.
 */
@Singleton
class PlayerManager @Inject constructor(
    private val exoPlayer: ExoPlayer,
) {
    private val _activePlayer = MutableStateFlow<Player>(exoPlayer)
    val activePlayer: StateFlow<Player> = _activePlayer

    fun currentPlayer(): Player = _activePlayer.value

    fun release() {
        (currentPlayer() as? ExoPlayer)?.release()
    }
}
