package nl.stoux.tfw.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository.AudioQuality

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlaybackControls(
    isPlaying: Boolean,
    canSkipPrevTrack: Boolean,
    canSkipNextTrack: Boolean,
    isOledModeActive: Boolean,
    audioQuality: AudioQuality,
    availableQualities: Set<AudioQuality> = AudioQuality.entries.toSet(),
    onPlayPause: () -> Unit,
    onPreviousLiveset: () -> Unit,
    onNextLiveset: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onToggleOledMode: () -> Unit,
    onAudioQualityChange: (AudioQuality) -> Unit,
    modifier: Modifier = Modifier,
    playPauseFocusRequester: FocusRequester? = null,
) {
    var showQualityDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous liveset
        IconButton(
            onClick = onPreviousLiveset,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous liveset",
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Previous track
        IconButton(
            onClick = onPreviousTrack,
            enabled = canSkipPrevTrack,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FastRewind,
                contentDescription = "Previous track",
                modifier = Modifier.size(28.dp),
                tint = if (canSkipPrevTrack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Play/Pause (larger, primary button)
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .then(
                    if (playPauseFocusRequester != null) Modifier.focusRequester(playPauseFocusRequester)
                    else Modifier
                )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Next track
        IconButton(
            onClick = onNextTrack,
            enabled = canSkipNextTrack,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FastForward,
                contentDescription = "Next track",
                modifier = Modifier.size(28.dp),
                tint = if (canSkipNextTrack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Next liveset
        IconButton(
            onClick = onNextLiveset,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next liveset",
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.width(24.dp))

        // OLED mode toggle
        IconButton(
            onClick = onToggleOledMode,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isOledModeActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
        ) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = if (isOledModeActive) "Exit OLED mode" else "Enter OLED mode",
                modifier = Modifier.size(24.dp),
                tint = if (isOledModeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Audio quality button
        Surface(
            onClick = { showQualityDialog = true },
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (audioQuality) {
                        AudioQuality.LOW -> "LQ"
                        AudioQuality.HIGH -> "HQ"
                        AudioQuality.LOSSLESS -> "WAV"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // Quality selection dialog
    if (showQualityDialog) {
        AudioQualityDialog(
            currentQuality = audioQuality,
            availableQualities = availableQualities,
            onQualitySelected = { quality ->
                onAudioQualityChange(quality)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AudioQualityDialog(
    currentQuality: AudioQuality,
    availableQualities: Set<AudioQuality>,
    onQualitySelected: (AudioQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Audio Quality",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.size(8.dp))

                AudioQuality.entries.forEach { quality ->
                    val isSelected = quality == currentQuality
                    val isAvailable = quality in availableQualities

                    Surface(
                        onClick = { if (isAvailable) onQualitySelected(quality) },
                        enabled = isAvailable,
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (quality) {
                                    AudioQuality.LOW -> "Low Quality (LQ)"
                                    AudioQuality.HIGH -> "High Quality (HQ)"
                                    AudioQuality.LOSSLESS -> "Lossless (WAV)"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = when {
                                    !isAvailable -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.size(8.dp))

                Surface(
                    onClick = onDismiss,
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.primary,
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
