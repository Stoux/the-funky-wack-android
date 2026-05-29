package nl.stoux.tfw.tv.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.network.dto.auth.DeviceDto
import nl.stoux.tfw.core.common.repository.DeviceRepository
import javax.inject.Inject

@HiltViewModel
class TvDevicesViewModel @Inject constructor(
    private val repository: DeviceRepository,
) : ViewModel() {
    val devices: StateFlow<List<DeviceDto>> = repository.devices

    init {
        viewModelScope.launch { repository.refresh() }
    }
}

@Composable
fun TvDevicesScreen(
    viewModel: TvDevicesViewModel = hiltViewModel(),
) {
    val devices by viewModel.devices.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        Text("Your devices", style = MaterialTheme.typography.headlineLarge)
        if (devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No devices registered.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(devices, key = { it.id }) { device ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    device.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                if (device.isCurrent) {
                                    Text(
                                        "This device",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            device.deviceType?.let { type ->
                                Text(type, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
