package nl.stoux.tfw.feature.player.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.network.dto.CommentDto
import nl.stoux.tfw.core.common.repository.CommentsRepository
import nl.stoux.tfw.core.common.repository.CommentsRepository.Sort
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
    authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommentsUiState())
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

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
        loadJob = viewModelScope.launch {
            commentsRepository.getComments(livesetId, sort)
                .onSuccess { comments ->
                    _uiState.update { if (it.livesetId == livesetId) it.copy(comments = comments, isLoading = false) else it }
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
