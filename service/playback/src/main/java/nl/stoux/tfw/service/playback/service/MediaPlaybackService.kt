package nl.stoux.tfw.service.playback.service

import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import nl.stoux.tfw.service.playback.player.PlayerManager

@AndroidEntryPoint
class MediaPlaybackService : MediaLibraryService() {

    @Inject
    lateinit var playerManager: PlayerManager

    private var mediaLibrarySession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val player = playerManager.currentPlayer()
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, SessionCallback())
            .setId("tfw-media-session")
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        playerManager.release()
        super.onDestroy()
    }

    private class SessionCallback : MediaLibrarySession.Callback
}
