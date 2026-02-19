package nl.stoux.tfw.tv.ui.player

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.database.entity.TrackEntity
import nl.stoux.tfw.feature.player.waveforms.WaveformDownloader
import nl.stoux.tfw.service.playback.service.MediaPlaybackService
import nl.stoux.tfw.service.playback.service.manager.LivesetTrackListener
import nl.stoux.tfw.service.playback.service.manager.LivesetTrackManager
import nl.stoux.tfw.service.playback.service.manager.UnbindCallback
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import javax.inject.Inject

@HiltViewModel
class TvNowPlayingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val livesetTrackManager: LivesetTrackManager,
    private val queueManager: QueueManager,
    private val waveformDownloader: WaveformDownloader,
) : ViewModel() {

    private var controller: MediaController? = null
    private var trackManagerUnbindCallback: UnbindCallback? = null

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    // Waveform data
    private val _waveformPeaks = MutableStateFlow<List<Int>?>(null)
    val waveformPeaks: StateFlow<List<Int>?> = _waveformPeaks.asStateFlow()

    // Current liveset and track
    private val _currentLiveset = MutableStateFlow<LivesetWithDetails?>(null)
    val currentLiveset: StateFlow<LivesetWithDetails?> = _currentLiveset.asStateFlow()

    private val _currentTrack = MutableStateFlow<TrackEntity?>(null)
    val currentTrack: StateFlow<TrackEntity?> = _currentTrack.asStateFlow()

    // Track skipping
    private val _canSkipPrevTrack = MutableStateFlow(false)
    val canSkipPrevTrack: StateFlow<Boolean> = _canSkipPrevTrack.asStateFlow()

    private val _canSkipNextTrack = MutableStateFlow(false)
    val canSkipNextTrack: StateFlow<Boolean> = _canSkipNextTrack.asStateFlow()

    // D-pad seek state
    private val _seekTargetProgress = MutableStateFlow<Float?>(null)
    val seekTargetProgress: StateFlow<Float?> = _seekTargetProgress.asStateFlow()

    private var progressJob: Job? = null

    init {
        trackManagerUnbindCallback = livesetTrackManager.bind(TrackListener())

        viewModelScope.launch {
            val token = SessionToken(appContext, ComponentName(appContext, MediaPlaybackService::class.java))
            val future = MediaController.Builder(appContext, token).buildAsync()
            future.addListener({
                controller = future.get()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying || controller?.playWhenReady == true
                        startOrStopProgressLoop(isPlaying)
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        updateDurations()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _isBuffering.value = controller?.isPlaying == false && controller?.playWhenReady == true
                        updateDurations()
                    }
                })

                // Initialize state
                _isPlaying.value = controller?.isPlaying == true || controller?.playWhenReady == true
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
                        _progress.value = if (dur > 0) (c.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
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
            _progress.value = if (_durationMs.value > 0) (_positionMs.value.toFloat() / _durationMs.value.toFloat()).coerceIn(0f, 1f) else 0f
        }
    }

    // Playback controls
    fun playPause() {
        controller?.let { c ->
            if (c.isPlaying) c.pause() else c.play()
        }
    }

    fun previousLiveset() {
        controller?.seekToPrevious()
    }

    fun nextLiveset() {
        viewModelScope.launch {
            queueManager.skipToNext()
        }
    }

    fun skipTrackBackward() {
        controller?.sendCustomCommand(MediaPlaybackService.commandPreviousTrack, bundleOf())
    }

    fun skipTrackForward() {
        controller?.sendCustomCommand(MediaPlaybackService.commandNextTrack, bundleOf())
    }

    fun seekTo(positionMs: Long) {
        controller?.let { c ->
            c.seekTo(c.currentMediaItemIndex, positionMs)
        }
        updateDurations()
    }

    fun seekToPercent(fraction: Float) {
        val dur = _durationMs.value
        if (dur > 0) {
            val pos = (dur * fraction.coerceIn(0f, 1f)).toLong()
            seekTo(pos)
        }
    }

    // D-pad waveform seeking
    fun startSeek() {
        _seekTargetProgress.value = _progress.value
    }

    fun adjustSeek(delta: Float) {
        val current = _seekTargetProgress.value ?: _progress.value
        _seekTargetProgress.value = (current + delta).coerceIn(0f, 1f)
    }

    fun confirmSeek() {
        _seekTargetProgress.value?.let { target ->
            seekToPercent(target)
        }
        _seekTargetProgress.value = null
    }

    fun cancelSeek() {
        _seekTargetProgress.value = null
    }

    fun seekToTrack(track: TrackEntity) {
        val timestampSec = track.timestampSec
        if (timestampSec != null) {
            seekTo(timestampSec.toLong() * 1000L)
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        progressJob = null
        controller?.release()
        controller = null
        trackManagerUnbindCallback?.invoke()
        trackManagerUnbindCallback = null
        super.onCleared()
    }

    private inner class TrackListener : LivesetTrackListener {
        override fun onNextPrevTrackStatusChanged(hasPreviousTrack: Boolean, hasNextTrack: Boolean) {
            _canSkipPrevTrack.value = hasPreviousTrack
            _canSkipNextTrack.value = hasNextTrack
        }

        override fun onTimeProgress(position: Long?, duration: Long?) {
            updateDurations()
        }

        override fun onLivesetChanged(liveset: LivesetWithDetails?) {
            _currentLiveset.value = liveset
            // Load waveform
            val waveformUrl = liveset?.liveset?.audioWaveformUrl
            if (waveformUrl != null) {
                _waveformPeaks.value = emptyList() // Loading state
                waveformDownloader.loadWaveform(waveformUrl) { data ->
                    _waveformPeaks.value = data
                }
            } else {
                _waveformPeaks.value = null
            }
        }

        override fun onTrackChanged(track: TrackEntity?) {
            _currentTrack.value = track
        }
    }
}
