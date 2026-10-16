package nl.stoux.tfw.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.network.dto.SearchResponse
import nl.stoux.tfw.core.common.repository.SearchRepository
import nl.stoux.tfw.core.common.repository.TrackRepository
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import nl.stoux.tfw.tracks.errorMessage
import javax.inject.Inject

data class SearchUiState(
    /** Trimmed query the [results] / [error] belong to, null when idle. */
    val query: String? = null,
    val isLoading: Boolean = false,
    val results: SearchResponse? = null,
    val error: String? = null,
) {
    val isEmpty: Boolean
        get() = results != null && results.livesets.isEmpty() && results.tracks.isEmpty() && results.unlinkedLines.isEmpty()
}

/**
 * Search-as-you-type. Typing is debounced (server is throttled at 60/min) and every new
 * query cancels the in-flight request, so stale results never land.
 *
 * Activity scoped (no NavHost), so the query survives a trip to a track page and back.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val queueManager: QueueManager,
    trackRepository: TrackRepository,
) : ViewModel() {

    /** Compose state (not a StateFlow) so the TextField never lags a frame behind typing. */
    var query by mutableStateOf("")
        private set

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    val likedSlugs: StateFlow<Set<String>> = trackRepository.likedSlugs

    private var searchJob: Job? = null
    private var scheduledQuery: String? = null

    fun onQueryChange(value: String) {
        query = value
        schedule(DEBOUNCE_MS, force = false)
    }

    /** Keyboard search action: skip the debounce, retry when the last attempt failed. */
    fun submit() {
        val s = _state.value
        schedule(0L, force = s.error != null || (s.results == null && !s.isLoading))
    }

    fun clear() {
        query = ""
        schedule(0L, force = false)
    }

    fun retry() = schedule(0L, force = true)

    private fun schedule(debounceMs: Long, force: Boolean) {
        val q = query.trim()

        if (q.length < SearchRepository.MIN_QUERY_LENGTH) {
            searchJob?.cancel()
            scheduledQuery = null
            _state.value = SearchUiState()
            return
        }

        // Same trimmed query as the last scheduled one (e.g. a trailing space): it's pending, in flight or done
        if (!force && q == scheduledQuery) {
            // Keyboard action during the debounce: fire right away
            if (debounceMs == 0L && searchJob?.isActive == true && !_state.value.isLoading) launchSearch(q, 0L)
            return
        }
        launchSearch(q, debounceMs)
    }

    private fun launchSearch(q: String, debounceMs: Long) {
        searchJob?.cancel()
        scheduledQuery = q
        searchJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            // Keep the previous results visible while the new ones load
            _state.value = _state.value.copy(query = q, isLoading = true, error = null)
            searchRepository.search(q)
                .onSuccess { _state.value = SearchUiState(query = q, results = it) }
                .onFailure { e ->
                    // The repo's runCatching swallows cancellation; a superseded search must stay silent
                    if (e is CancellationException) throw e
                    _state.value = SearchUiState(query = q, error = errorMessage(e, "Search failed."))
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
        private const val DEBOUNCE_MS = 300L
    }
}
