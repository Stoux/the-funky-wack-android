package nl.stoux.tfw.tv.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.database.entity.artworkUrl
import nl.stoux.tfw.core.common.device.DeviceIdManager
import nl.stoux.tfw.core.common.repository.EditionRepository
import nl.stoux.tfw.core.common.repository.PlaybackPositionRepository
import javax.inject.Inject

data class TvContinueItem(
    val liveset: LivesetWithDetails,
    val positionSeconds: Long,
    val deviceLabel: String?,
    val deviceType: String?,
)

@HiltViewModel
class TvContinueListeningViewModel @Inject constructor(
    private val positionRepository: PlaybackPositionRepository,
    private val editionRepository: EditionRepository,
    private val deviceIdManager: DeviceIdManager,
    authRepository: AuthRepository,
) : ViewModel() {

    val items: StateFlow<List<TvContinueItem>> = combine(
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
                TvContinueItem(
                    liveset = lwd,
                    positionSeconds = pos.position,
                    deviceLabel = pos.device?.displayName ?: pos.device?.deviceName,
                    deviceType = pos.device?.deviceType,
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn

    init { viewModelScope.launch { positionRepository.refresh() } }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvContinueListeningRow(
    onResume: (livesetId: Long, positionSeconds: Long) -> Unit,
    viewModel: TvContinueListeningViewModel = hiltViewModel(),
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val items by viewModel.items.collectAsState()

    if (!isLoggedIn || items.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(
            "Continue listening",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
        ) {
            items(items, key = { it.liveset.liveset.id }) { item ->
                ContinueCardTv(item = item, onClick = { onResume(item.liveset.liveset.id, item.positionSeconds) })
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContinueCardTv(item: TvContinueItem, onClick: () -> Unit) {
    val artwork = item.liveset.edition.artworkUrl
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = Modifier.width(220.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier.size(196.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (artwork != null) {
                    AsyncImage(model = artwork, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    Text("TFW #${item.liveset.edition.number}", style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                item.liveset.liveset.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                item.liveset.liveset.artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                "From ${formatTime(item.positionSeconds)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            item.deviceLabel?.let { label ->
                Text(
                    "on $label",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
