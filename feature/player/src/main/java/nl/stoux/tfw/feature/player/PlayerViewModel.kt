package nl.stoux.tfw.feature.player

import android.content.ComponentName
import android.content.Context
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.database.entity.TrackEntity
import nl.stoux.tfw.service.playback.service.MediaPlaybackService
import nl.stoux.tfw.service.playback.service.manager.LivesetTrackListener
import nl.stoux.tfw.service.playback.service.session.CustomMediaId
import nl.stoux.tfw.service.playback.service.manager.UnbindCallback
import nl.stoux.tfw.service.playback.service.manager.LivesetTrackManager

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val livesetTrackManager: LivesetTrackManager,
) : ViewModel() {

    private var controller: MediaController? = null

    // Region: Core playback state
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

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering

    private var progressJob: Job? = null


    private val _currentLiveset = MutableStateFlow<LivesetWithDetails?>(null)
    val currentLiveset = _currentLiveset.asStateFlow()

    private val _currentTrack = MutableStateFlow<TrackEntity?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    // Region: Extended UI state (dummy values for now)
    val appTitle: StateFlow<String> = MutableStateFlow("The Funky Wack")
    private val _hasCast = MutableStateFlow(false) // TODO detect cast availability
    val hasCast: StateFlow<Boolean> = _hasCast
    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled

    // Liveset track skipping state
    private val _canSkipPrevTrack = MutableStateFlow(false)
    val canSkipPrevTrack: StateFlow<Boolean> = _canSkipPrevTrack.asStateFlow()
    private val _canSkipNextTrack = MutableStateFlow(false)
    val canSkipNextTrack: StateFlow<Boolean> = _canSkipNextTrack.asStateFlow()

    var trackManagerUnbindCallback: UnbindCallback? = null

    init {
        // Bind to LivesetTrackManager for track segment skipping within livesets
        trackManagerUnbindCallback = livesetTrackManager.bind(TrackListener())

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
                        _isBuffering.value = controller?.isPlaying == false && controller?.playWhenReady == true
                        updateDurations()
                    }
                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        _shuffleEnabled.value = shuffleModeEnabled
                    }
                })

                // Initialize state
                _isPlaying.value = controller?.isPlaying == true || controller?.playWhenReady == true
                _nowPlayingTitle.value = controller?.currentMediaItem?.mediaMetadata?.title?.toString()
                _shuffleEnabled.value = controller?.shuffleModeEnabled ?: false
                _isBuffering.value = controller?.isPlaying == false && controller?.playWhenReady == true
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

    fun playLiveset(mediaId: CustomMediaId) {
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

    fun previousLiveset() {
        controller?.seekToPrevious()
    }

    fun nextLiveset() {
        controller?.seekToNext()
    }

    fun skipTrackBackward() {
        controller?.sendCustomCommand(MediaPlaybackService.commandPreviousTrack, bundleOf())
    }

    fun skipTrackForward() {
        controller?.sendCustomCommand(MediaPlaybackService.commandNextTrack, bundleOf())
    }

    fun toggleShuffle() {
        controller?.let { c ->
            val newVal = !(c.shuffleModeEnabled)
            c.shuffleModeEnabled = newVal
            _shuffleEnabled.value = newVal
        }
    }

    fun openQueue() {
        // TODO: Implement navigation to queue screen or show bottom sheet
    }

    fun onCloseRequested() {
        // TODO: Hook to navigation back
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
        // Unbind from liveset track manager
        trackManagerUnbindCallback?.invoke()
        trackManagerUnbindCallback = null
    }

    private inner class TrackListener : LivesetTrackListener {

        override fun onNextPrevTrackStatusChanged(
            hasPreviousTrack: Boolean,
            hasNextTrack: Boolean
        ) {
            _canSkipPrevTrack.value = hasPreviousTrack
            _canSkipNextTrack.value = hasNextTrack
        }

        override fun onTimeProgress(position: Long?, duration: Long?) {
            updateDurations()
        }

        override fun onLivesetChanged(liveset: LivesetWithDetails?) {
            _currentLiveset.value = liveset
        }

        override fun onTrackChanged(track: TrackEntity?) {
            _currentTrack.value = track
        }


    }

}
