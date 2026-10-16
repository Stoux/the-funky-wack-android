package nl.stoux.tfw.tv.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.network.dto.SearchResponse
import nl.stoux.tfw.core.common.repository.SearchRepository
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import javax.inject.Inject

data class TvSearchUiState(
    val status: Status = Status.Idle,
    /** Query the [results] belong to. */
    val resultsQuery: String = "",
    /** Last results; kept while a new query is loading so the list doesn't flash empty. */
    val results: SearchResponse? = null,
) {
    enum class Status { Idle, Loading, Results, Error }

    val isEmptyResult: Boolean
        get() = results != null && results.livesets.isEmpty() && results.tracks.isEmpty() && results.unlinkedLines.isEmpty()
}

@HiltViewModel
class TvSearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val queueManager: QueueManager,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    private val _state = MutableStateFlow(TvSearchUiState())
    val state: StateFlow<TvSearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        if (query.value.isNotBlank()) schedule(query.value, debounce = false)
    }

    fun onQueryChange(value: String) {
        if (value == query.value) return
        savedStateHandle[KEY_QUERY] = value
        schedule(value, debounce = true)
    }

    /** IME Search / Retry: search right away, skipping the debounce. */
    fun submit() {
        val current = _state.value
        val q = query.value.trim()
        // IME Search after the debounced request already landed: nothing new to ask
        if (current.status == TvSearchUiState.Status.Results && current.resultsQuery == q) return
        schedule(query.value, debounce = false)
    }

    fun play(livesetId: Long, timestampSec: Int?) {
        queueManager.setContextFromLiveset(
            livesetId = livesetId,
            startPositionMs = (timestampSec ?: 0) * 1000L,
            autoplay = true,
        )
    }

    /** Server throttles at 60/min: debounce typing and cancel whatever request is still in flight. */
    private fun schedule(raw: String, debounce: Boolean) {
        searchJob?.cancel()
        val q = raw.trim()
        if (q.length < SearchRepository.MIN_QUERY_LENGTH) {
            _state.value = TvSearchUiState()
            return
        }
        searchJob = viewModelScope.launch {
            if (debounce) delay(DEBOUNCE_MS)
            _state.update { it.copy(status = TvSearchUiState.Status.Loading) }
            val result = searchRepository.search(q, RESULT_LIMIT)
            // The repository's runCatching also swallows cancellation, so don't publish a stale answer
            ensureActive()
            _state.value = result.fold(
                onSuccess = { TvSearchUiState(TvSearchUiState.Status.Results, q, it) },
                onFailure = { TvSearchUiState(TvSearchUiState.Status.Error, q, null) },
            )
        }
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val DEBOUNCE_MS = 400L
        const val RESULT_LIMIT = 20
    }
}
