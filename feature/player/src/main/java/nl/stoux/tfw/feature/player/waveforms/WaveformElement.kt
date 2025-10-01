package nl.stoux.tfw.feature.player.waveforms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min


private const val MINIMUM_ZOOM = 1f
private const val MAXIMUM_ZOOM = 12f

/**
 * Zoomable and pannable waveform. Maintains its own zoom/pan state.
 * - Pinch to zoom (1x..12x).
 * - Drag with two fingers (transform pan) to move window; one-finger swipe is also supported via transform pan delta.
 * - Tap to seek within the visible window.
 */
@Composable
fun ZoomableWaveform(
    fullPeaks: List<Int>,
    progress: Float,
    modifier: Modifier = Modifier,
    onProgressChange: (Float) -> Unit,
    waveformBrush: Brush = Brush.verticalGradient(listOf(Color(0xFF444444), Color(0xFF666666))),
    progressBrush: Brush = Brush.verticalGradient(listOf(Color.White, Color.LightGray)),
) {
    var zoom by remember { mutableFloatStateOf(MINIMUM_ZOOM) }
    var pan by remember { mutableFloatStateOf(0f) } // pan in indices
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(fullPeaks) {
        // Reset the zoom/pan when the waveform changes
        zoom = MINIMUM_ZOOM
        pan = 0f
    }

    // Derived window
    val total = fullPeaks.size
    val windowSize = remember(total, zoom) {
        (if (total == 0) 0 else (total / zoom)).toInt().coerceIn(0, total)
    }
    val safeWindow = if (windowSize <= 0 || total == 0) 0 else windowSize

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(total) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val widthPx = containerSize.width.toFloat().coerceAtLeast(1f)
                    val oldZoom = zoom
                    val newZoom = (oldZoom * zoomChange).coerceIn(MINIMUM_ZOOM, MAXIMUM_ZOOM)

                    val oldWindow = total / oldZoom
                    val newWindow = total / newZoom

                    // Convert pan in pixels to indices (samples) using the pre-zoom window
                    val pxToSamples = (oldWindow.takeIf { it > 0 } ?: total.toFloat()) / widthPx
                    val panSamplesDelta = -panChange.x * pxToSamples

                    // Anchor zoom at the gesture focus point to avoid sliding feel
                    val focusFrac = (centroid.x / widthPx).coerceIn(0f, 1f)
                    val panAnchorAdjust = focusFrac * (oldWindow - newWindow)

                    zoom = newZoom
                    pan = (pan + panAnchorAdjust + panSamplesDelta)
                        .coerceIn(0f, (total - newWindow).coerceAtLeast(0f))
                }
            }
    ) {
        Waveform(
            allPeaks = fullPeaks,
            currentProgress = progress,
            windowSize = safeWindow,
            pan = pan,
            onProgressChange = onProgressChange,
            waveformBrush = waveformBrush,
            progressBrush = progressBrush,
            modifier = Modifier.matchParentSize()
        )
    }
}

/**
 * Simple waveform renderer drawing spikes for the given peaks list.
 * Taps update progress via [onProgressChange]. Use [ZoomableWaveform] for pinch-zoom and pan.
 *
 * @param windowSize The number of peaks that should be displayed for the current zoom level, should match allPeaks.size when fully zoomed out
 */
@Composable
private fun Waveform(
    allPeaks: List<Int>,
    currentProgress: Float,
    windowSize: Int,
    modifier: Modifier = Modifier,
    pan: Float = 0f,
    onProgressChange: (Float) -> Unit = {},
    waveformBrush: Brush = Brush.verticalGradient(listOf(Color(0xFF444444), Color(0xFF666666))),
    progressBrush: Brush = Brush.verticalGradient(listOf(Color.White, Color.LightGray)),
) {

    // Target a SoundCloud-like thin bar look
    val density = LocalDensity.current
    val barWidth = 2.dp
    val gapWidth = 1.5.dp
    val minBarHeightPx = 2f

    // Keep track of how many bars can fit into a single screen
    var barsFit by remember { mutableIntStateOf(0) }

    // Compute visible window in samples (indices)
    val totalSamples = allPeaks.size
    val safeWindow = windowSize.coerceIn(0, totalSamples)
    val startSample = remember(pan, safeWindow, totalSamples) {
        if (safeWindow <= 0 || totalSamples == 0) 0
        else pan.toInt().coerceIn(0, (totalSamples - safeWindow).coerceAtLeast(0))
    }
    val endSample = (startSample + safeWindow).coerceAtMost(totalSamples)

    // Compute a grid-aligned stride for the current zoom that stays stable while panning
    val samplesPerBar = remember(safeWindow, barsFit) {
        val win = safeWindow.coerceAtLeast(1)
        val fit = barsFit.coerceAtLeast(1)
        ceil(win / fit.toFloat()).toInt().coerceAtLeast(1)
    }

    // Precompute the entire waveform into bars using the current stride, aligned from index 0
    val fullBars = remember(allPeaks, samplesPerBar) {
        if (allPeaks.isEmpty()) emptyList() else allPeaks.chunked(samplesPerBar) { it.maxOrNull() ?: 0 }
    }

    // Determine visible bar window based on pan, using the same bars grid
    val startBarIndex = remember(startSample, samplesPerBar, fullBars) {
        floor(startSample / samplesPerBar.toFloat()).toInt().coerceIn(0, (fullBars.size - 1).coerceAtLeast(0))
    }
    val endBarIndexExclusive = (startBarIndex + barsFit).coerceAtMost(fullBars.size)
    val visibleBars = if (startBarIndex < endBarIndexExclusive) fullBars.subList(startBarIndex, endBarIndexExclusive) else emptyList()

    Canvas(
        modifier = modifier
            .pointerInput(totalSamples, samplesPerBar, startBarIndex, barsFit) {
                detectTapGestures(
                    onTap = { offset ->
                        // Map local X -> global progress using the bars grid for stability
                        val localFrac = (offset.x / size.width).coerceIn(0f, 1f)
                        val tappedBar = startBarIndex + localFrac * barsFit.toFloat()
                        val approxSample = tappedBar * samplesPerBar
                        val newProgress = if (totalSamples > 0) (approxSample / totalSamples.toFloat()).coerceIn(0f, 1f) else 0f
                        onProgressChange(newProgress)
                    }
                )
            }
            .onSizeChanged { size ->
                val barWidthPx = with(density) { barWidth.toPx() }
                val gapWidthPx = with(density) { gapWidth.toPx() }
                barsFit = floor(size.width / (barWidthPx + gapWidthPx)).toInt().coerceAtLeast(1)
            }
    ) {
        if (safeWindow <= 0 || totalSamples == 0 || barsFit <= 0 || visibleBars.isEmpty()) return@Canvas

        val visibleCount = visibleBars.size

        // Progress mapped using the same bars grid for consistency
        val absoluteSample = (currentProgress.coerceIn(0f, 1f) * totalSamples.toFloat())
        val absoluteBarFloat = absoluteSample / samplesPerBar.toFloat()
        val visibleProgress = ((absoluteBarFloat - startBarIndex) / visibleCount.toFloat()).coerceIn(0f, 1f)

        // Normalize to the local window max to keep visuals balanced
        val maxPeak = (visibleBars.maxOrNull() ?: 1).coerceAtLeast(1)

        // Step between spike left edges
        val stepX = size.width / visibleCount
        val spikeWidth = min(barWidth.toPx(), stepX * 0.9f) // keep thin even when zoomed in

        // Mono representation covering full height: draw bars from bottom up to fill available height
        val maxBarHeight = size.height

        visibleBars.forEachIndexed { index, p ->
            val amp = (p.toFloat() / maxPeak.toFloat()).coerceIn(0f, 1f)
            val spikeHeight = (amp * maxBarHeight).coerceAtLeast(minBarHeightPx)
            val x = index * stepX + (stepX - spikeWidth) / 2f
            val y = (maxBarHeight - spikeHeight)

            val currentSpikeProgress = (index + 1f) / visibleCount.toFloat()
            val isPlayed = currentSpikeProgress <= visibleProgress

            val brush = if (isPlayed) progressBrush else waveformBrush

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(spikeWidth, spikeHeight),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
}