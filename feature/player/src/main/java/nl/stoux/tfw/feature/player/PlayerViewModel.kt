package nl.stoux.tfw.feature.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.service.playback.service.MediaPlaybackService

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private var controller: MediaController? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _nowPlayingTitle = MutableStateFlow<String?>(null)
    val nowPlayingTitle: StateFlow<String?> = _nowPlayingTitle

    init {
        // Build a MediaController connected to our service
        viewModelScope.launch {
            val token = SessionToken(appContext, ComponentName(appContext, MediaPlaybackService::class.java))
            val future = MediaController.Builder(appContext, token).buildAsync()
            future.addListener({
                controller = future.get()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        _nowPlayingTitle.value = mediaItem?.mediaMetadata?.title?.toString()
                    }
                })
            }, { runnable -> runnable.run() })
        }
    }

    fun playUrl(url: String, title: String? = null, artist: String? = null) {
        viewModelScope.launch {
            val item = MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .build()
                )
                .build()
            controller?.let { c ->
                c.setMediaItem(item)
                c.prepare()
                c.play()
            }
        }
    }

    fun playPause() {
        controller?.let { c ->
            if (c.isPlaying) c.pause() else c.play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }
}
