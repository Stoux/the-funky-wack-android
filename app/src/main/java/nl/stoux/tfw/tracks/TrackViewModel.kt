package nl.stoux.tfw.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.network.dto.TrackDetailResponse
import nl.stoux.tfw.core.common.network.dto.TrackSong
import nl.stoux.tfw.core.common.repository.TrackRepository
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import javax.inject.Inject

sealed interface TrackStatus {
    data object Loading : TrackStatus
    data class Loaded(val detail: TrackDetailResponse) : TrackStatus
    data object NotFound : TrackStatus
    data class Error(val message: String) : TrackStatus
}

data class TrackPageState(val slug: String? = null, val status: TrackStatus = TrackStatus.Loading)

/**
 * Backs the track page. Activity scoped and shared by every track page on the stack, so it
 * keeps a small cache: going back to the previous track shows it right away and revalidates.
 */
@HiltViewModel
class TrackViewModel @Inject constructor(
    private val trackRepository: TrackRepository,
    private val queueManager: QueueManager,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TrackPageState())
    val state: StateFlow<TrackPageState> = _state.asStateFlow()

    val likedSlugs: StateFlow<Set<String>> = trackRepository.likedSlugs
    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val cache = object : LinkedHashMap<String, TrackDetailResponse>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrackDetailResponse>?) = size > CACHE_SIZE
    }
    private var loadJob: Job? = null

    fun open(slug: String) {
        val current = _state.value
        // Already showing (or loading) this one
        if (current.slug == slug && current.status !is TrackStatus.Error) return
        load(slug)
    }

    fun retry() {
        _state.value.slug?.let { load(it) }
    }

    private fun load(slug: String) {
        loadJob?.cancel()
        val cached = cache[slug]
        _state.value = TrackPageState(slug, cached?.let { TrackStatus.Loaded(it) } ?: TrackStatus.Loading)
        loadJob = viewModelScope.launch {
            trackRepository.getTrack(slug)
                .onSuccess {
                    cache[slug] = it
                    _state.value = TrackPageState(slug, TrackStatus.Loaded(it))
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    when {
                        // Merged away: the repo refreshes likes on its own
                        isNotFound(e) -> {
                            cache.remove(slug)
                            _state.value = TrackPageState(slug, TrackStatus.NotFound)
                        }
                        // Keep showing the cached copy when revalidating fails
                        cached != null -> Unit
                        else -> _state.value = TrackPageState(slug, TrackStatus.Error(errorMessage(e, "Couldn't load this track.")))
                    }
                }
        }
    }

    fun setLiked(song: TrackSong, liked: Boolean) {
        viewModelScope.launch {
            trackRepository.setLiked(song, liked).onFailure { e ->
                if (e is CancellationException) throw e
                _messages.tryEmit(
                    if (isNotFound(e)) "This track no longer exists."
                    else errorMessage(e, if (liked) "Couldn't like this track." else "Couldn't unlike this track.")
                )
            }
        }
    }

    fun play(livesetId: Long, timestampSeconds: Int?) {
        queueManager.setContextFromLiveset(
            livesetId,
            startPositionMs = (timestampSeconds ?: 0) * 1000L,
            autoplay = true,
        )
    }

    companion object {
        private const val CACHE_SIZE = 20
    }
}
