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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
    val allowLossless by viewModel.allowLossless.collectAsState()
    var showLosslessConfirmDialog by remember { mutableStateOf(false) }

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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        // Only allow digits
                        textValue = newValue.filter { it.isDigit() }.take(3)
                    },
                    modifier = Modifier
                        .width(56.dp)
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                // Apply value on blur
                                val parsed = textValue.toIntOrNull()
                                if (parsed != null && parsed in 1..999) {
                                    viewModel.setBufferMinutes(parsed)
                                } else {
                                    // Reset to current value if invalid
                                    textValue = bufferMinutes.toString()
                                }
                            }
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.End)
                )
                Text(
                    text = "min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Audio quality
        SettingsRow(
            title = "Default audio quality",
            description = "Quality used when starting playback"
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.width(140.dp)
            ) {
                // Low and High on first row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AudioQuality.LOW, AudioQuality.HIGH).forEach { quality ->
                        val isSelected = quality == audioQuality
                        Surface(
                            onClick = { viewModel.setAudioQuality(quality) },
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
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
                // Lossless on second row - full width of column
                val isLosslessSelected = audioQuality == AudioQuality.LOSSLESS
                Surface(
                    onClick = {
                        if (allowLossless) {
                            viewModel.setAudioQuality(AudioQuality.LOSSLESS)
                        } else {
                            showLosslessConfirmDialog = true
                        }
                    },
                    color = if (isLosslessSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = AudioQuality.LOSSLESS.label + if (!allowLossless) " ⚠️" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isLosslessSelected)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Allow lossless toggle
        SettingsRow(
            title = "Allow lossless playback",
            description = "Enable WAV quality option (files can be ~1GB per set)"
        ) {
            Switch(
                checked = allowLossless,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        showLosslessConfirmDialog = true
                    } else {
                        viewModel.setAllowLossless(false)
                        // If current quality is lossless, switch to HQ
                        if (audioQuality == AudioQuality.LOSSLESS) {
                            viewModel.setAudioQuality(AudioQuality.HIGH)
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Info text
        Text(
            text = "Buffer changes take effect immediately. Higher buffer values use more memory but provide better playback through tunnels and spotty coverage.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Lossless confirmation dialog
    if (showLosslessConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLosslessConfirmDialog = false },
            title = { Text("Enable lossless playback?") },
            text = {
                Text(
                    "Lossless WAV files are massive - typically around 1GB per set. " +
                    "This will use significant data and storage.\n\n" +
                    "High Quality (HQ) is indistinguishable from lossless for 99% of listeners " +
                    "and uses a fraction of the bandwidth.\n\n" +
                    "Are you sure you want to enable lossless playback?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setAllowLossless(true)
                        viewModel.setAudioQuality(AudioQuality.LOSSLESS)
                        showLosslessConfirmDialog = false
                    }
                ) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLosslessConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
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
