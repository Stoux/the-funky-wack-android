package nl.stoux.tfw.tv.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.stoux.tfw.tv.ui.common.TvEmptyState
import nl.stoux.tfw.tv.ui.common.TvListCard
import nl.stoux.tfw.tv.ui.common.TvScreenScaffold
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.device.DeviceIdManager
import nl.stoux.tfw.core.common.network.dto.auth.DeviceDto
import nl.stoux.tfw.core.common.repository.DeviceRepository
import javax.inject.Inject

@HiltViewModel
class TvDevicesViewModel @Inject constructor(
    private val repository: DeviceRepository,
    deviceIdManager: DeviceIdManager,
) : ViewModel() {
    val devices: StateFlow<List<DeviceDto>> = repository.devices
    val currentClientId: String = deviceIdManager.clientId

    init {
        viewModelScope.launch { repository.refresh() }
    }
}

@Composable
fun TvDevicesScreen(
    onBack: () -> Unit,
    viewModel: TvDevicesViewModel = hiltViewModel(),
) {
    val devices by viewModel.devices.collectAsState()

    TvScreenScaffold(
        title = "Your devices",
        subtitle = "Manage devices on your phone or the website",
        onBack = onBack,
    ) {
        if (devices.isEmpty()) {
            TvEmptyState("No devices registered.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(devices, key = { it.id }) { device ->
                    val isCurrent = device.clientId == viewModel.currentClientId
                    // Focusable (no action) so the D-pad can scroll through a long list
                    TvListCard(onClick = {}) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = device.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (isCurrent) {
                                Text(
                                    text = "This device",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        device.deviceType?.let { type ->
                            Text(
                                text = when (type.lowercase()) {
                                    "tv" -> "TV"
                                    "car", "automotive" -> "Car"
                                    else -> type.replaceFirstChar { it.uppercase() }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
