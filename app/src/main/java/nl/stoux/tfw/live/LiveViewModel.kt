package nl.stoux.tfw.live

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.auth.LiveSessionDto
import javax.inject.Inject

data class LiveUiState(
    val sessions: List<LiveSessionDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val api: ApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveUiState())
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                Log.d("LiveViewModel", "Fetching live sessions...")
                val sessions = api.getLiveSessions().sessions
                Log.d("LiveViewModel", "Got ${sessions.size} live sessions: ${sessions.map { "${it.channelToken}: ${it.host.name} playing ${it.liveset?.title ?: "?"} (${it.listenersCount} listeners)" }}")
                _uiState.value = _uiState.value.copy(
                    sessions = sessions,
                    isLoading = false,
                )
            } catch (e: Exception) {
                Log.e("LiveViewModel", "Failed to fetch live sessions", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load live sessions",
                )
            }
        }
    }
}
