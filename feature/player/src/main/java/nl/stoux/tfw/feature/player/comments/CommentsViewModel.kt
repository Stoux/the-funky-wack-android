package nl.stoux.tfw.feature.player.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.network.dto.CommentDto
import nl.stoux.tfw.core.common.network.dto.CommentLiveEvent
import nl.stoux.tfw.core.common.network.dto.LiveCommentDto
import nl.stoux.tfw.core.common.repository.CommentsRepository
import nl.stoux.tfw.core.common.repository.CommentsRepository.Sort
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import retrofit2.HttpException
import javax.inject.Inject

data class CommentsUiState(
    val livesetId: Long? = null,
    val comments: List<CommentDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val sort: Sort = Sort.TIMESTAMP,
    val isSubmitting: Boolean = false,
)

@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val commentsRepository: CommentsRepository,
    private val authRepository: AuthRepository,
    queueManager: QueueManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    private val live = MutableStateFlow(false)

    init {
        // Follow realtime comments on the playing set while the app is in the foreground
        viewModelScope.launch {
            combine(queueManager.state.map { it.currentLivesetId }, live) { livesetId, isLive -> livesetId.takeIf { isLive } }
                .distinctUntilChanged()
                .flatMapLatest { livesetId ->
                    if (livesetId == null) emptyFlow()
                    else commentsRepository.liveEvents(livesetId).map { livesetId to it }
                }
                .collect { (livesetId, event) -> applyLiveEvent(livesetId, event) }
        }
    }

    /** Receive realtime comment changes while the app is visible. */
    fun setLive(enabled: Boolean) {
        live.value = enabled
    }

    /** Live payloads received while a fetch was in flight, re-applied on top of its response. */
    private val liveDuringLoad = mutableListOf<LiveCommentDto>()

    private suspend fun applyLiveEvent(livesetId: Long, event: CommentLiveEvent) {
        val payload = event.comment
        val myId = authRepository.currentUser.value?.id
        val state = _uiState.value
        val isLoaded = state.livesetId == livesetId

        // The event name only matters for the bubble: someone else's comment we didn't know yet
        val isNew = !(isLoaded && state.comments.containsComment(payload.id))
        if (event is CommentLiveEvent.Posted && !payload.isDeleted && isNew && (myId == null || payload.user?.id != myId)) {
            showBubble(payload)
        }

        // List not loaded for this set: the first fetch brings it (count comes from the editions data until then)
        if (!isLoaded) return
        if (state.isLoading) {
            liveDuringLoad += payload
            if (state.comments.isEmpty()) return
        }

        val applied = applyServerState(state.comments, payload, myId, state.sort)
        if (applied == null) {
            // Reply to a parent we don't have locally
            refresh()
            return
        }
        if (applied == state.comments) return
        _uiState.update { if (it.livesetId == livesetId) it.copy(comments = applied) else it }
        commentsRepository.syncLocalCount(livesetId, applied)
    }

    /** A comment someone else just posted on the playing set, shown briefly over the waveform. */
    private val _bubble = MutableStateFlow<LiveCommentDto?>(null)
    val bubble: StateFlow<LiveCommentDto?> = _bubble.asStateFlow()
    private var bubbleJob: Job? = null

    private fun showBubble(comment: LiveCommentDto) {
        bubbleJob?.cancel()
        _bubble.value = comment
        bubbleJob = viewModelScope.launch {
            delay(BUBBLE_DURATION_MS)
            _bubble.value = null
        }
    }

    private fun LiveCommentDto.toComment(myId: Long?) = CommentDto(
        id = id,
        user = user,
        body = body,
        isDeleted = isDeleted,
        timestampSeconds = timestampSeconds,
        parentId = parentId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isOwn = myId != null && user?.id == myId,
        replies = if (parentId == null) emptyList() else null,
    )

    private fun List<CommentDto>.containsComment(id: Long): Boolean =
        any { top -> top.id == id || top.replies.orEmpty().any { it.id == id } }

    /**
     * Apply a broadcast payload as the comment's current server state, whatever the event name. Queued
     * broadcasts are built from the row at send time, so e.g. an `updated` can already be deleted.
     * Returns null for a reply whose parent isn't loaded (caller refetches).
     */
    private fun applyServerState(comments: List<CommentDto>, live: LiveCommentDto, myId: Long?, sort: Sort): List<CommentDto>? {
        val known = comments.containsComment(live.id)
        if (live.isDeleted) {
            if (!known) return comments
            fun CommentDto.placeholder() = copy(body = null, isDeleted = true, updatedAt = live.updatedAt)
            return comments.mapNotNull { top ->
                when {
                    // Same as the API: a deleted top-level comment only stays as placeholder when it has replies
                    top.id == live.id && top.replies.isNullOrEmpty() -> null
                    top.id == live.id -> top.placeholder()
                    else -> top.copy(replies = top.replies?.map { if (it.id == live.id) it.placeholder() else it })
                }
            }
        }
        if (known) return applyFields(comments, live)

        val parentId = live.parentId ?: return sorted(comments + live.toComment(myId), sort)
        if (comments.none { it.id == parentId }) return null
        return comments.map { top ->
            if (top.id == parentId) top.copy(replies = top.replies.orEmpty() + live.toComment(myId)) else top
        }
    }

    /** Replace [live]'s fields on the matching comment or reply, keeping local is_own and replies. */
    private fun applyFields(comments: List<CommentDto>, live: LiveCommentDto): List<CommentDto> {
        fun CommentDto.applied() = if (id != live.id) this else copy(
            user = live.user,
            body = live.body,
            isDeleted = live.isDeleted,
            updatedAt = live.updatedAt,
        )
        return comments.map { top -> top.applied().copy(replies = top.replies?.map { it.applied() }) }
    }

    private fun sorted(comments: List<CommentDto>, sort: Sort): List<CommentDto> = when (sort) {
        Sort.TIMESTAMP -> comments.sortedWith(compareBy<CommentDto> { it.timestampSeconds }.thenBy { it.createdAt })
        Sort.DATE -> comments.sortedBy { it.createdAt }
    }

    private companion object {
        const val BUBBLE_DURATION_MS = 5_000L
    }

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn

    /** Admins can delete anyone's comment (server-side check is authoritative). */
    val isAdmin: StateFlow<Boolean> = authRepository.currentUser
        .map { it?.isAdmin == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var loadJob: Job? = null

    /** (Re)load comments for [livesetId]. Keeps the current list visible when reloading the same set. */
    fun load(livesetId: Long?) {
        loadJob?.cancel()
        if (livesetId == null) {
            _uiState.update { it.copy(livesetId = null, comments = emptyList(), isLoading = false, error = null) }
            return
        }
        _uiState.update {
            if (it.livesetId == livesetId) it.copy(isLoading = true, error = null)
            else it.copy(livesetId = livesetId, comments = emptyList(), isLoading = true, error = null)
        }
        val sort = _uiState.value.sort
        liveDuringLoad.clear()
        loadJob = viewModelScope.launch {
            commentsRepository.getComments(livesetId, sort)
                .onSuccess { comments ->
                    // Changes that arrived during the request but aren't in the response would otherwise vanish
                    val myId = authRepository.currentUser.value?.id
                    val merged = liveDuringLoad
                        .filter { !comments.containsComment(it.id) }
                        .fold(comments) { acc, live -> applyServerState(acc, live, myId, sort) ?: acc }
                    liveDuringLoad.clear()
                    _uiState.update { if (it.livesetId == livesetId) it.copy(comments = merged, isLoading = false) else it }
                    if (merged != comments) commentsRepository.syncLocalCount(livesetId, merged)
                }
                .onFailure { e ->
                    // A newer load() cancelled us; let that one own the state.
                    if (e is CancellationException) return@onFailure
                    _uiState.update {
                        if (it.livesetId == livesetId) it.copy(isLoading = false, error = errorMessage(e)) else it
                    }
                }
        }
    }

    fun refresh() = load(_uiState.value.livesetId)

    fun setSort(sort: Sort) {
        if (_uiState.value.sort == sort) return
        _uiState.update { it.copy(sort = sort) }
        refresh()
    }

    /** Post a new top-level comment at [timestampSeconds], or a reply when [parentId] is set. */
    fun post(body: String, timestampSeconds: Int, parentId: Long? = null, onSuccess: () -> Unit) {
        val livesetId = _uiState.value.livesetId ?: return
        submit(onSuccess) {
            commentsRepository.create(livesetId, body.trim(), timestampSeconds, parentId)
        }
    }

    fun edit(commentId: Long, body: String, onSuccess: () -> Unit) {
        submit(onSuccess) { commentsRepository.update(commentId, body.trim()) }
    }

    fun delete(commentId: Long) {
        submit(onSuccess = {}) { commentsRepository.delete(commentId) }
    }

    private fun submit(onSuccess: () -> Unit, action: suspend () -> Result<*>) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val result = action()
            _uiState.update { it.copy(isSubmitting = false) }
            result
                .onSuccess {
                    onSuccess()
                    // Re-fetch so ordering, reply placement and deleted placeholders match the server.
                    refresh()
                }
                .onFailure { _messages.tryEmit(errorMessage(it)) }
        }
    }

    private fun errorMessage(e: Throwable): String = when ((e as? HttpException)?.code()) {
        401 -> "Log in again to comment"
        403 -> "You're not allowed to do that"
        404 -> "Comment no longer exists"
        422 -> "Comment rejected, check the text"
        429 -> "Slow down, try again in a minute"
        null -> "Couldn't reach the server"
        else -> "Something went wrong"
    }
}
