package nl.stoux.tfw.feature.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository.AudioQuality

/**
 * Dialog for selecting download quality.
 */
@Composable
fun DownloadQualityDialog(
    livesetTitle: String,
    qualityOptions: List<QualityOptionInfo>,
    defaultQuality: AudioQuality,
    onConfirm: (AudioQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    // Pre-select the default quality if available, otherwise the best available
    val availableQualities = qualityOptions.map { it.quality }
    val initialSelection = if (defaultQuality in availableQualities) {
        defaultQuality
    } else {
        availableQualities.lastOrNull() ?: AudioQuality.HIGH
    }
    var selectedQuality by remember { mutableStateOf(initialSelection) }

    val selectedOption = qualityOptions.find { it.quality == selectedQuality }
    val isLosslessSelected = selectedQuality == AudioQuality.LOSSLESS
    val isLargeLossless = isLosslessSelected && (selectedOption?.fileSize ?: 0) > 500_000_000 // > 500MB

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Download Quality")
        },
        text = {
            Column {
                Text(
                    text = livesetTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )

                Spacer(modifier = Modifier.height(16.dp))

                qualityOptions.forEach { option ->
                    QualityOption(
                        option = option,
                        isSelected = option.quality == selectedQuality,
                        onClick = { selectedQuality = option.quality }
                    )
                }

                if (isLargeLossless) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "This lossless file is very large. Make sure you have enough storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedQuality) }) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun QualityOption(
    option: QualityOptionInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.quality.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            )
            Text(
                text = option.format,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (option.fileSize != null) {
            Text(
                text = formatFileSize(option.fileSize),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

/**
 * Warning dialog for downloading on cellular data.
 */
@Composable
fun CellularDownloadWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Download on Cellular?")
        },
        text = {
            Text(
                "You're on mobile data. Downloading may use a significant amount of your data plan. " +
                "Continue anyway?"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
