package nl.stoux.tfw.feature.player.waveforms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min


private const val MINIMUM_ZOOM = 1f
private const val DEFAULT_MAXIMUM_ZOOM = 12f

/**
 * Zoomable and pannable waveform. Maintains its own zoom/pan state.
 * - Pinch to zoom (1x..maxZoom).
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
    heightMultiplier: Float = 1f,
    maxZoom: Float = DEFAULT_MAXIMUM_ZOOM,
) {
    var zoom by remember { mutableFloatStateOf(MINIMUM_ZOOM) }
    var pan by remember { mutableFloatStateOf(0f) } // pan in indices
    var containerSize by remember { mutableStateOf(IntSize.Zero) }


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
                    val newZoom = (oldZoom * zoomChange).coerceIn(MINIMUM_ZOOM, maxZoom)

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
            modifier = Modifier.matchParentSize(),
            heightMultiplier = heightMultiplier
        )
    }
}

@Composable
fun AnimatedZoomableWaveform(
    trackKey: Any?,
    fullPeaks: List<Int>,
    progress: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    waveformBrush: Brush = Brush.verticalGradient(listOf(Color(0xFF444444), Color(0xFF666666))),
    progressBrush: Brush = Brush.verticalGradient(listOf(Color.White, Color.LightGray)),
    durationMs: Long? = null,
    maxZoomOverride: Float? = null,
) {
    val height = remember { Animatable(1f) }

    // Collapse when the track changes
    LaunchedEffect(trackKey) {
        // animate down; if first composition and no change, it's fine to animate quickly
        height.animateTo(0f, animationSpec = tween(durationMillis = 300))
    }

    // Grow in when peaks are ready (non-empty call site ensures this)
    LaunchedEffect(fullPeaks) {
        if (fullPeaks.isNotEmpty()) {
            height.snapTo(0f)
            height.animateTo(1f, animationSpec = tween(durationMillis = 400))
        }
    }

    // Compute dynamic max zoom: 30 min → 12f, 60 min → 18f, +6f per extra 60 min
    val computedMaxZoom = maxZoomOverride ?: run {
        val minutes = ((durationMs ?: 0L).toFloat() / 60000f)
        val extraFactor = max(0f, (minutes - 30f) / 60f) // 0 at 30m, 0.5 at 60m, 1.0 at 90m
        (DEFAULT_MAXIMUM_ZOOM * (1f + extraFactor)).coerceAtMost(48f)
    }

    ZoomableWaveform(
        fullPeaks = fullPeaks,
        progress = progress,
        onProgressChange = onProgressChange,
        modifier = modifier,
        waveformBrush = waveformBrush,
        progressBrush = progressBrush,
        heightMultiplier = height.value,
        maxZoom = computedMaxZoom
    )
}

/**
 * Simple waveform renderer drawing spikes for the given peaks list.
 * Taps update progress via [onProgressChange]. Use [ZoomableWaveform] for pinch-zoom and pan.
 *
 * @param windowSize The number of peaks that should be displayed for the current zoom level, should match allPeaks.size when fully zoomed out
 */
private data class WaveBar(val avg: Float, val peak: Float)

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
    heightMultiplier: Float = 1f,
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
    val fullBars: List<WaveBar> = remember(allPeaks, samplesPerBar) {
        if (allPeaks.isEmpty()) emptyList() else {
            val globalPeak = (allPeaks.maxOrNull() ?: 1).coerceAtLeast(1)
            val norm = 1f / globalPeak.toFloat()
            allPeaks.chunked(samplesPerBar) { chunk ->
                val maxVal = (chunk.maxOrNull() ?: 0).toFloat()
                // Use P60 (60th percentile) within the chunk as the inner/average metric for better perceived loudness
                val p60 = if (chunk.isEmpty()) 0f else run {
                    val sorted = chunk.sorted()
                    val idx = ((sorted.size - 1) * 0.60f).toInt().coerceIn(0, sorted.lastIndex)
                    sorted[idx].toFloat()
                }
                WaveBar(
                    avg = (p60 * norm).coerceIn(0f, 1f),
                    peak = (maxVal * norm).coerceIn(0f, 1f)
                )
            }
        }
    }

    // Determine visible bar window based on pan, using the same bars grid
    val startBarIndex = remember(startSample, samplesPerBar, fullBars) {
        floor(startSample / samplesPerBar.toFloat()).toInt().coerceIn(0, (fullBars.size - 1).coerceAtLeast(0))
    }
    val endBarIndexExclusive = (startBarIndex + barsFit).coerceAtMost(fullBars.size)
    val visibleBars = if (startBarIndex < endBarIndexExclusive) fullBars.subList(startBarIndex, endBarIndexExclusive) else emptyList()

    // Helper to compute progress from a local X coordinate using the current bars grid
    fun computeProgressFromX(
        x: Float,
        canvasWidth: Float,
        startBarIndex: Int,
        barsFit: Int,
        samplesPerBar: Int,
        totalSamples: Int,
    ): Float {
        if (canvasWidth <= 0f || totalSamples <= 0 || barsFit <= 0 || samplesPerBar <= 0) return 0f
        val localFrac = (x / canvasWidth).coerceIn(0f, 1f)
        val barFloat = startBarIndex + localFrac * barsFit.toFloat()
        val approxSample = barFloat * samplesPerBar
        return (approxSample / totalSamples.toFloat()).coerceIn(0f, 1f)
    }

    Canvas(
        modifier = modifier
            // One-finger press and drag to seek continuously
            .pointerInput(totalSamples, samplesPerBar, startBarIndex, barsFit) {
                detectDragGesturesAfterLongPress(onDragStart = { offset ->
                    val newProgress = computeProgressFromX(
                        x = offset.x,
                        canvasWidth = size.width.toFloat(),
                        startBarIndex = startBarIndex,
                        barsFit = barsFit,
                        samplesPerBar = samplesPerBar,
                        totalSamples = totalSamples
                    )
                    onProgressChange(newProgress)
                }, onDrag = { change, _ ->
                    val newProgress = computeProgressFromX(
                        x = change.position.x,
                        canvasWidth = size.width.toFloat(),
                        startBarIndex = startBarIndex,
                        barsFit = barsFit,
                        samplesPerBar = samplesPerBar,
                        totalSamples = totalSamples
                    )
                    onProgressChange(newProgress)
                    change.consume()
                })
            }
            // Tap to seek with ripple feedback
            .pointerInput(totalSamples, samplesPerBar, startBarIndex, barsFit) {
                detectTapGestures(
                    onTap = { offset ->
                        val newProgress = computeProgressFromX(
                            x = offset.x,
                            canvasWidth = size.width.toFloat(),
                            startBarIndex = startBarIndex,
                            barsFit = barsFit,
                            samplesPerBar = samplesPerBar,
                            totalSamples = totalSamples
                        )
                        onProgressChange(newProgress)
                    })
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

        // Stage B: viewport-local normalization using percentile of visible peaks
        val localPeaks = visibleBars.map { it.peak }.sorted()
        val p95Index = ((localPeaks.size - 1) * 0.95f).toInt().coerceIn(0, (localPeaks.size - 1).coerceAtLeast(0))
        val localRef = (if (localPeaks.isNotEmpty()) localPeaks[p95Index] else 1f).coerceIn(0.2f, 1.0f)
        val gLocal = 1f / localRef
        val alpha = 0.5f // blend to reduce pumping
        val g = 1f * (1 - alpha) + gLocal * alpha

        // Step between spike left edges
        val stepX = size.width / visibleCount
        val spikeWidth = min(barWidth.toPx(), stepX * 0.9f) // keep thin even when zoomed in

        // Mono representation covering full height: draw bars from bottom up to fill available height
        val maxBarHeight = size.height


        // --- A. PLAYED STATE (Bright White) ---
        // High-contrast, active, and clean.

        // Base colors for the gradient
        val playedBaseColorStart = Color.White
        val playedBaseColorEnd = Color(0xFFF5F5F5) // A very slightly off-white gray

        // 1. OUTER (Max) Brush - The subtle "glow"
        // This is drawn FIRST.
        val outerBrushPlayed = Brush.verticalGradient(
            listOf(
                playedBaseColorStart.copy(alpha = 0.3f), // 30% opacity
                playedBaseColorEnd.copy(alpha = 0.3f)
            )
        )

        // 2. INNER (Average) Brush - The solid "body"
        // This is drawn SECOND (on top).
        val innerBrushPlayed = Brush.verticalGradient(
            listOf(
                playedBaseColorStart.copy(alpha = 0.95f), // 95% opacity
                playedBaseColorEnd.copy(alpha = 0.95f)
            )
        )


        // --- B. UNPLAYED STATE (Subtle Light Gray) ---
        // Muted, inactive, and recedes visually.

        // Base colors for the gradient
        val unplayedBaseColorStart = Color(0xFFB0BEC5) // Material Blue Gray 200
        val unplayedBaseColorEnd = Color(0xFF90A4AE)   // Material Blue Gray 300

        // 1. OUTER (Max) Brush - The very subtle "glow"
        // This is drawn FIRST.
        val outerBrushUnplayed = Brush.verticalGradient(
            listOf(
                unplayedBaseColorStart.copy(alpha = 0.2f), // 20% opacity (as requested)
                unplayedBaseColorEnd.copy(alpha = 0.2f)
            )
        )

        // 2. INNER (Average) Brush - The "body"
        // This is drawn SECOND (on top).
        val innerBrushUnplayed = Brush.verticalGradient(
            listOf(
                unplayedBaseColorStart.copy(alpha = 0.6f), // 60% opacity (clearly inactive)
                unplayedBaseColorEnd.copy(alpha = 0.6f)
            )
        )


        visibleBars.forEachIndexed { index, b ->
            val peakV = (b.peak * g).coerceIn(0f, 1f)
            val avgV = (b.avg * g).coerceIn(0f, 1f)

            val hPeak = (peakV * maxBarHeight * heightMultiplier).coerceAtLeast(minBarHeightPx)
            val rawHAvg = (avgV * maxBarHeight * heightMultiplier).coerceAtLeast(minBarHeightPx)
            val hAvg = min(rawHAvg, hPeak)

            val x = index * stepX + (stepX - spikeWidth) / 2f
            val yPeak = (maxBarHeight - hPeak)
            val yAvg = (maxBarHeight - hAvg)

            val currentSpikeProgress = (index + 1f) / visibleCount.toFloat()
            val isPlayed = currentSpikeProgress <= visibleProgress

            // Colors per played state:
            // Played: Redish (peak), Greenish (avg)
            // Not played: Grayish Redish (peak), Grayish Greenish (avg)
            val peakBrush = if (isPlayed) outerBrushPlayed else outerBrushUnplayed
            val avgBrush = if (isPlayed) innerBrushPlayed else innerBrushUnplayed

            // Draw peak (outer/background)
            drawRoundRect(
                brush = peakBrush,
                topLeft = Offset(x, yPeak),
                size = Size(spikeWidth, hPeak),
                cornerRadius = CornerRadius(2.dp.toPx())
            )

            // Draw average on top with same width as peak, clamped to not exceed peak height
            drawRoundRect(
                brush = avgBrush,
                topLeft = Offset(x, yAvg),
                size = Size(spikeWidth, hAvg),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }

    }
}