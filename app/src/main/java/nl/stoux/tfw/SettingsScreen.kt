package nl.stoux.tfw

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository.AudioQuality

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val bufferMinutes by viewModel.bufferMinutes.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Playback section
        Text(
            text = "Playback",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Buffer duration
        SettingsRow(
            title = "Buffer duration",
            description = "Minutes of audio to buffer ahead (1-999)"
        ) {
            var textValue by remember(bufferMinutes) { mutableStateOf(bufferMinutes.toString()) }

            OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    // Only allow digits
                    val filtered = newValue.filter { it.isDigit() }.take(3)
                    textValue = filtered
                    val parsed = filtered.toIntOrNull()
                    if (parsed != null && parsed in 1..999) {
                        viewModel.setBufferMinutes(parsed)
                    }
                },
                modifier = Modifier.width(80.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                suffix = { Text("min") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Audio quality
        SettingsRow(
            title = "Default audio quality",
            description = "Quality used when starting playback"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioQuality.entries.forEach { quality ->
                    val isSelected = quality == audioQuality
                    Surface(
                        onClick = { viewModel.setAudioQuality(quality) },
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = quality.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Info text
        Text(
            text = "Buffer changes take effect immediately. Higher buffer values use more memory but provide better playback through tunnels and spotty coverage.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        content()
    }
}
