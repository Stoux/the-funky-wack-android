package nl.stoux.tfw.tv.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val SEEK_DELTA = 0.01f // 1% per D-pad press

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvWaveformDisplay(
    peaks: List<Int>?,
    progress: Float,
    isPlaying: Boolean,
    seekTargetProgress: Float?,
    onSeekStart: () -> Unit,
    onSeekAdjust: (Float) -> Unit,
    onSeekConfirm: () -> Unit,
    onSeekCancel: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val isSeekingMode = seekTargetProgress != null

    // Use rememberUpdatedState to capture latest values for callbacks
    val currentSeekTargetProgress by rememberUpdatedState(seekTargetProgress)
    val currentOnSeekCancel by rememberUpdatedState(onSeekCancel)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                onFocusChanged(focusState.isFocused)
                if (!focusState.isFocused && currentSeekTargetProgress != null) {
                    currentOnSeekCancel()
                }
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (!isSeekingMode) onSeekStart()
                            onSeekAdjust(-SEEK_DELTA)
                            true
                        }
                        Key.DirectionRight -> {
                            if (!isSeekingMode) onSeekStart()
                            onSeekAdjust(SEEK_DELTA)
                            true
                        }
                        Key.Enter, Key.DirectionCenter -> {
                            if (isSeekingMode) {
                                onSeekConfirm()
                            }
                            true
                        }
                        Key.Back, Key.Escape -> {
                            if (isSeekingMode) {
                                onSeekCancel()
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            // Cancel seek when navigating away
                            if (isSeekingMode) {
                                currentOnSeekCancel()
                            }
                            false // Let the event propagate for navigation
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            peaks == null -> {
                Text(
                    text = "No waveform available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            peaks.isEmpty() -> {
                Text(
                    text = "Loading waveform...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            else -> {
                WaveformCanvas(
                    peaks = peaks,
                    progress = progress,
                    isPlaying = isPlaying,
                    seekTargetProgress = seekTargetProgress,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun WaveformCanvas(
    peaks: List<Int>,
    progress: Float,
    isPlaying: Boolean,
    seekTargetProgress: Float?,
    modifier: Modifier = Modifier,
) {
    val displayProgress = seekTargetProgress ?: progress

    Canvas(modifier = modifier) {
        val barWidth = 3.dp.toPx()
        val gapWidth = 2.dp.toPx()
        val minBarHeight = 4f

        val barsFit = floor(size.width / (barWidth + gapWidth)).toInt().coerceAtLeast(1)
        val samplesPerBar = ceil(peaks.size / barsFit.toFloat()).toInt().coerceAtLeast(1)

        // Compute bars
        val globalPeak = (peaks.maxOrNull() ?: 1).coerceAtLeast(1)
        val norm = 1f / globalPeak.toFloat()

        val bars = peaks.chunked(samplesPerBar) { chunk ->
            val maxVal = (chunk.maxOrNull() ?: 0).toFloat()
            val sorted = chunk.sorted()
            val p60Idx = ((sorted.size - 1) * 0.60f).toInt().coerceIn(0, sorted.lastIndex)
            val p60 = sorted[p60Idx].toFloat()
            Pair(
                (p60 * norm).coerceIn(0f, 1f),
                (maxVal * norm).coerceIn(0f, 1f)
            )
        }

        val stepX = size.width / bars.size
        val maxBarHeight = size.height

        // Colors - grayer when paused
        val pausedAlphaMultiplier = if (isPlaying) 1f else 0.5f
        val playedOuter = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.3f * pausedAlphaMultiplier),
                Color.White.copy(alpha = 0.3f * pausedAlphaMultiplier)
            )
        )
        val playedInner = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.95f * pausedAlphaMultiplier),
                Color(0xFFF5F5F5).copy(alpha = 0.95f * pausedAlphaMultiplier)
            )
        )
        val unplayedOuter = Brush.verticalGradient(
            listOf(
                Color(0xFFB0BEC5).copy(alpha = 0.2f * pausedAlphaMultiplier),
                Color(0xFF90A4AE).copy(alpha = 0.2f * pausedAlphaMultiplier)
            )
        )
        val unplayedInner = Brush.verticalGradient(
            listOf(
                Color(0xFFB0BEC5).copy(alpha = 0.6f * pausedAlphaMultiplier),
                Color(0xFF90A4AE).copy(alpha = 0.6f * pausedAlphaMultiplier)
            )
        )
        val seekIndicatorColor = Color(0xFF4CAF50) // Green for seek indicator

        bars.forEachIndexed { index, (avg, peak) ->
            val hPeak = (peak * maxBarHeight).coerceAtLeast(minBarHeight)
            val hAvg = (avg * maxBarHeight).coerceAtLeast(minBarHeight).coerceAtMost(hPeak)

            val x = index * stepX + (stepX - barWidth) / 2f
            val yPeak = maxBarHeight - hPeak
            val yAvg = maxBarHeight - hAvg

            val barProgress = (index + 1f) / bars.size
            val isPlayed = barProgress <= displayProgress

            val outerBrush = if (isPlayed) playedOuter else unplayedOuter
            val innerBrush = if (isPlayed) playedInner else unplayedInner

            // Draw peak (outer)
            drawRoundRect(
                brush = outerBrush,
                topLeft = Offset(x, yPeak),
                size = Size(barWidth, hPeak),
                cornerRadius = CornerRadius(2.dp.toPx())
            )

            // Draw average (inner)
            drawRoundRect(
                brush = innerBrush,
                topLeft = Offset(x, yAvg),
                size = Size(barWidth, hAvg),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }

        // Draw seek indicator line when in seeking mode
        if (seekTargetProgress != null) {
            val seekX = size.width * seekTargetProgress
            drawLine(
                color = seekIndicatorColor,
                start = Offset(seekX, 0f),
                end = Offset(seekX, size.height),
                strokeWidth = 3.dp.toPx()
            )
        }

    }
}
