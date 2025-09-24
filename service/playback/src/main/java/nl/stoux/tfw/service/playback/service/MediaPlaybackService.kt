package nl.stoux.tfw.service.playback.service

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MediaPlaybackService : MediaLibraryService() {

    @Inject
    lateinit var exoPlayer: ExoPlayer

    private var mediaLibrarySession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        mediaLibrarySession = MediaLibrarySession.Builder(this, exoPlayer, SessionCallback())
            .setId("tfw-media-session")
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        exoPlayer.release()
        super.onDestroy()
    }

    private class SessionCallback : MediaLibrarySession.Callback
}
