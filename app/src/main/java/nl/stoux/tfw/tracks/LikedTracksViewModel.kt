package nl.stoux.tfw.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.network.dto.LikedTrackDto
import nl.stoux.tfw.core.common.network.dto.TrackSong
import nl.stoux.tfw.core.common.repository.TrackRepository
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import javax.inject.Inject

@HiltViewModel
class LikedTracksViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    private val queueManager: QueueManager,
) : ViewModel() {

    /** Last liked first. */
    val likedTracks: StateFlow<List<LikedTrackDto>> = trackRepository.likedTracks
    val isLoading: StateFlow<Boolean> = trackRepository.isLoadingLikes

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun refresh() {
        viewModelScope.launch { trackRepository.refreshLikes() }
    }

    fun unlike(track: LikedTrackDto) {
        viewModelScope.launch {
            trackRepository.setLiked(TrackSong(track.slug, track.artist, track.title, track.version), liked = false)
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    if (!isNotFound(e)) _messages.tryEmit(errorMessage(e, "Couldn't unlike this track."))
                }
        }
    }

    /** Play the most recent appearance (appearances are newest edition first). False when it was never played. */
    fun playLatest(track: LikedTrackDto): Boolean {
        val appearance = track.appearances.firstOrNull() ?: return false
        queueManager.setContextFromLiveset(
            appearance.livesetId,
            startPositionMs = (appearance.timestamp ?: 0) * 1000L,
            autoplay = true,
        )
        return true
    }
}
