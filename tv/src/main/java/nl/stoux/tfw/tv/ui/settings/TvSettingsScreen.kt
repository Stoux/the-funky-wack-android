package nl.stoux.tfw.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository.AudioQuality

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsScreen(
    onBack: () -> Unit,
    viewModel: TvSettingsViewModel = hiltViewModel(),
) {
    val oledSettings by viewModel.oledSettings.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()
    val bufferMinutes by viewModel.bufferMinutes.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Surface(
                    onClick = onBack,
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Back",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        // Audio section header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Audio",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Audio quality
        item {
            SettingsOptionRow(
                title = "Default quality",
                options = AudioQuality.entries.toList(),
                selectedOption = audioQuality,
                onOptionSelected = { viewModel.setAudioQuality(it) },
                labelTransform = {
                    when (it) {
                        AudioQuality.LOW -> "LQ"
                        AudioQuality.HIGH -> "HQ"
                        AudioQuality.LOSSLESS -> "Lossless"
                    }
                }
            )
        }

        // Buffer duration
        item {
            SettingsOptionRow(
                title = "Buffer duration",
                options = listOf(1, 5, 10, 15, 30),
                selectedOption = bufferMinutes,
                onOptionSelected = { viewModel.setBufferMinutes(it) },
                labelTransform = { "$it min" }
            )
        }

        // OLED Protection section header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "OLED Protection",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Auto-enable toggle
        item {
            SettingsToggleRow(
                title = "Auto-enable after inactivity",
                description = "Automatically activate OLED protection when idle",
                isChecked = oledSettings.autoEnable,
                onCheckedChange = { viewModel.setOledAutoEnable(it) }
            )
        }

        // Timeout selector (only if auto-enable is on)
        if (oledSettings.autoEnable) {
            item {
                SettingsOptionRow(
                    title = "Timeout",
                    options = listOf(2, 5, 10),
                    selectedOption = oledSettings.timeoutMinutes,
                    onOptionSelected = { viewModel.setOledTimeout(it) },
                    labelTransform = { "$it min" }
                )
            }
        }

        // Effects section header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Effects",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Drift animation
        item {
            SettingsToggleRow(
                title = "Drift animation",
                description = "Slowly move artwork to prevent burn-in",
                isChecked = oledSettings.driftEnabled,
                onCheckedChange = { viewModel.setOledDriftEnabled(it) }
            )
        }

        // Fade elements
        item {
            SettingsToggleRow(
                title = "Fade elements",
                description = "Hide UI elements after inactivity",
                isChecked = oledSettings.fadeEnabled,
                onCheckedChange = { viewModel.setOledFadeEnabled(it) }
            )
        }

        // Color cycling
        item {
            SettingsToggleRow(
                title = "Color cycling",
                description = "Shift waveform colors slowly",
                isChecked = oledSettings.colorShiftEnabled,
                onCheckedChange = { viewModel.setOledColorShiftEnabled(it) }
            )
        }

        // Bottom padding
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onCheckedChange(!isChecked) },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            // Toggle indicator
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isChecked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(4.dp),
                contentAlignment = if (isChecked) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun <T> SettingsOptionRow(
    title: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    labelTransform: (T) -> String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                Surface(
                    onClick = { onOptionSelected(option) },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = labelTransform(option),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
