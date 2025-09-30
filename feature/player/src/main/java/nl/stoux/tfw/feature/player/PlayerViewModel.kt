package nl.stoux.tfw.feature.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.service.playback.service.MediaPlaybackService
import nl.stoux.tfw.service.playback.service.session.CustomMediaId

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private var controller: MediaController? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _nowPlayingTitle = MutableStateFlow<String?>(null)
    val nowPlayingTitle: StateFlow<String?> = _nowPlayingTitle

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress

    private var progressJob: Job? = null

    init {
        // Build a MediaController connected to our service
        viewModelScope.launch {
            val token = SessionToken(appContext, ComponentName(appContext, MediaPlaybackService::class.java))
            val future = MediaController.Builder(appContext, token).buildAsync()
            future.addListener({
                controller = future.get()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying || controller?.playWhenReady == true;
                        startOrStopProgressLoop(isPlaying)
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        _nowPlayingTitle.value = mediaItem?.mediaMetadata?.title?.toString()
                        updateDurations()
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updateDurations()
                    }
                })
                // Initialize state
                _isPlaying.value = controller?.isPlaying == true || controller?.playWhenReady == true
                _nowPlayingTitle.value = controller?.currentMediaItem?.mediaMetadata?.title?.toString()
                updateDurations()
                startOrStopProgressLoop(_isPlaying.value)
            }, { runnable -> runnable.run() })
        }
    }

    private fun startOrStopProgressLoop(shouldRun: Boolean) {
        if (shouldRun) {
            if (progressJob?.isActive == true) return
            progressJob = viewModelScope.launch {
                while (true) {
                    val c = controller
                    if (c != null) {
                        _positionMs.value = c.currentPosition
                        val dur = c.duration.takeIf { it > 0 } ?: _durationMs.value
                        _durationMs.value = dur
                        _progress.value = if (dur > 0) (c.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else null
                    }
                    delay(500)
                }
            }
        } else {
            progressJob?.cancel()
            progressJob = null
        }
    }

    private fun updateDurations() {
        val c = controller
        if (c != null) {
            val dur = c.duration
            if (dur > 0) _durationMs.value = dur
            _positionMs.value = c.currentPosition
            _progress.value = if (_durationMs.value > 0) (_positionMs.value.toFloat() / _durationMs.value.toFloat()).coerceIn(0f, 1f) else null
        }
    }

    fun playUrl(mediaId: CustomMediaId) {
        viewModelScope.launch {
            val item = MediaItem.Builder()
                .setMediaId(mediaId.original)
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
        _positionMs.value = positionMs
        if (_durationMs.value > 0) {
            _progress.value = (positionMs.toFloat() / _durationMs.value.toFloat()).coerceIn(0f, 1f)
        }
    }

    fun seekToPercent(fraction: Float) {
        val dur = _durationMs.value
        if (dur > 0) {
            val pos = (dur * fraction.coerceIn(0f, 1f)).toLong()
            seekTo(pos)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Stop progress updates and release the controller to avoid leaks
        progressJob?.cancel()
        progressJob = null
        controller?.release()
        controller = null
    }
}
