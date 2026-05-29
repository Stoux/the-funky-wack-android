package nl.stoux.tfw.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import nl.stoux.tfw.core.common.network.dto.auth.DeviceDto

@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val devices by viewModel.devices.collectAsState()
    var renameTarget by remember { mutableStateOf<DeviceDto?>(null) }

    if (devices.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No devices yet.", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(devices, key = { it.id }) { device ->
                DeviceCard(
                    device = device,
                    isCurrent = device.clientId == viewModel.currentClientId,
                    onRename = { renameTarget = device },
                    onToggleHidden = { viewModel.setHidden(device.id, !device.isHidden) },
                    onRemove = { viewModel.remove(device.id) },
                )
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            currentName = target.displayName,
            onConfirm = { newName ->
                viewModel.rename(target.id, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}

@Composable
private fun DeviceCard(
    device: DeviceDto,
    isCurrent: Boolean,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (isCurrent) {
                    Text("This device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            device.deviceType?.let { type ->
                Text(type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (device.isStale) {
                Text("Inactive", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onRename) { Text("Rename") }
                TextButton(onClick = onToggleHidden) { Text(if (device.isHidden) "Show" else "Hide") }
                if (!isCurrent) {
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename device") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
