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
import nl.stoux.tfw.core.common.device.DeviceIdManager
import nl.stoux.tfw.core.common.repository.EditionRepository
import nl.stoux.tfw.core.common.repository.PlaybackPositionRepository
import javax.inject.Inject

data class ContinueListenItem(
    val liveset: LivesetWithDetails,
    val positionSeconds: Long,
    val deviceLabel: String?,
    val deviceType: String?,
)

@HiltViewModel
class ContinueListeningViewModel @Inject constructor(
    private val positionRepository: PlaybackPositionRepository,
    private val editionRepository: EditionRepository,
    private val deviceIdManager: DeviceIdManager,
    authRepository: AuthRepository,
) : ViewModel() {

    val items: StateFlow<List<ContinueListenItem>> = combine(
        positionRepository.positions,
        editionRepository.cachedLivesets,
    ) { positions, _ ->
        val currentClientId = deviceIdManager.clientId
        positions
            .filter { it.device?.clientId?.let { id -> id != currentClientId } == true }
            .groupBy { it.device!!.clientId!! }
            .mapNotNull { (_, group) -> group.maxByOrNull { it.updatedAt ?: "" } }
            .sortedByDescending { it.updatedAt ?: "" }
            .mapNotNull { pos ->
                val lwd = editionRepository.getCachedLiveset(pos.livesetId) ?: return@mapNotNull null
                ContinueListenItem(
                    liveset = lwd,
                    positionSeconds = pos.position,
                    deviceLabel = pos.device?.displayName ?: pos.device?.deviceName,
                    deviceType = pos.device?.deviceType,
                )
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
