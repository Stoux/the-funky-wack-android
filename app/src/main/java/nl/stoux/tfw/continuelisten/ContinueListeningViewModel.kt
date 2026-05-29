package nl.stoux.tfw.continuelisten

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.repository.EditionRepository
import nl.stoux.tfw.core.common.repository.PlaybackPositionRepository
import javax.inject.Inject

data class ContinueListenItem(
    val liveset: LivesetWithDetails,
    val positionSeconds: Long,
)

@HiltViewModel
class ContinueListeningViewModel @Inject constructor(
    private val positionRepository: PlaybackPositionRepository,
    private val editionRepository: EditionRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val items: StateFlow<List<ContinueListenItem>> = combine(
        positionRepository.positions,
        editionRepository.cachedLivesets,
    ) { positions, _ ->
        positions.mapNotNull { pos ->
            val lwd = editionRepository.getCachedLiveset(pos.livesetId) ?: return@mapNotNull null
            ContinueListenItem(liveset = lwd, positionSeconds = pos.position)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn

    init {
        // Pull latest positions when the section first appears.
        viewModelScope.launch { positionRepository.refresh() }
    }

    fun refresh() {
        viewModelScope.launch { positionRepository.refresh() }
    }
}
