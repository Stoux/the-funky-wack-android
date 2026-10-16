package nl.stoux.tfw.tv.ui.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.network.dto.CommentDto
import nl.stoux.tfw.core.common.repository.CommentsRepository
import nl.stoux.tfw.core.common.repository.EditionRepository
import javax.inject.Inject

/** A comment as rendered in the TV panel: top-level comments followed by their replies. */
data class TvCommentRow(
    val comment: CommentDto,
    val isReply: Boolean,
)

data class TvCommentsUiState(
    val livesetId: Long? = null,
    val rows: List<TvCommentRow> = emptyList(),
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
)

/**
 * Read-only comments for the TV now playing screen. Fetches when the panel opens and,
 * while it stays open, refetches on any realtime event (no local merge logic).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TvCommentsViewModel @Inject constructor(
    private val commentsRepository: CommentsRepository,
    editionRepository: EditionRepository,
) : ViewModel() {

    private val boundLivesetId = MutableStateFlow<Long?>(null)

    /** Stored count for the playing liveset; updated by every fetch via [CommentsRepository.syncLocalCount]. */
    val commentsCount: StateFlow<Int?> = boundLivesetId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else editionRepository.findLiveset(id).map { it?.liveset?.commentsCount }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _uiState = MutableStateFlow(TvCommentsUiState())
    val uiState: StateFlow<TvCommentsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var liveJob: Job? = null

    /** Follow the stored comment count of the playing liveset. */
    fun bind(livesetId: Long?) {
        boundLivesetId.value = livesetId
    }

    /** Panel opened: fetch and follow realtime changes until [close]. */
    fun open(livesetId: Long) {
        if (_uiState.value.livesetId != livesetId) {
            _uiState.value = TvCommentsUiState(livesetId = livesetId)
        }
        load(livesetId)

        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            commentsRepository.liveEvents(livesetId).collectLatest {
                // Cheap debounce: a burst of events results in a single refetch
                delay(LIVE_REFETCH_DELAY_MS)
                load(livesetId)
            }
        }
    }

    fun close() {
        liveJob?.cancel()
        liveJob = null
    }

    fun retry() {
        _uiState.value.livesetId?.let { load(it) }
    }

    private fun load(livesetId: Long) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = commentsRepository.getComments(livesetId, CommentsRepository.Sort.TIMESTAMP)
            // getComments uses runCatching, so a cancelled fetch shows up as a failure
            ensureActive()
            _uiState.update { state ->
                if (state.livesetId != livesetId) return@update state
                result.fold(
                    onSuccess = { comments ->
                        state.copy(rows = flatten(comments), isLoading = false, hasLoaded = true, error = null)
                    },
                    onFailure = {
                        state.copy(isLoading = false, hasLoaded = true, error = "Couldn't load comments")
                    },
                )
            }
        }
    }

    private fun flatten(comments: List<CommentDto>): List<TvCommentRow> =
        comments
            .sortedBy { it.timestampSeconds } // stable, keeps server order on ties
            .flatMap { top ->
                listOf(TvCommentRow(top, isReply = false)) +
                    top.replies.orEmpty().map { TvCommentRow(it, isReply = true) }
            }

    override fun onCleared() {
        close()
        super.onCleared()
    }

    private companion object {
        const val LIVE_REFETCH_DELAY_MS = 400L
    }
}
