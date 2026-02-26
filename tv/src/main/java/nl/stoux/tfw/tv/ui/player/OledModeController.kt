package nl.stoux.tfw.tv.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import nl.stoux.tfw.tv.data.TvSettingsRepository.OledSettings
import kotlin.random.Random

@Stable
class OledModeController {
    // Overall dim effect (1f = normal, 0.3f = dimmed)
    val dimAlpha = Animatable(1f)

    // UI visibility for fade effect (1f = visible, 0f = hidden)
    val uiVisibility = Animatable(1f)

    // Artwork drift offsets (in dp)
    val artworkOffsetX = Animatable(0f)
    val artworkOffsetY = Animatable(0f)

    // Waveform hue shift (0f - 360f)
    val waveformHue = Animatable(0f)

    // Track if we're currently in OLED mode
    private var isActive = false

    suspend fun enterOledMode(settings: OledSettings) {
        if (isActive) return
        isActive = true

        coroutineScope {
            // Dim everything
            launch {
                dimAlpha.animateTo(
                    targetValue = DIMMED_ALPHA,
                    animationSpec = tween(durationMillis = FADE_DURATION_MS)
                )
            }

            // Fade out UI if enabled
            if (settings.fadeEnabled) {
                launch {
                    uiVisibility.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = FADE_DURATION_MS)
                    )
                }
            }
        }
    }

    suspend fun exitOledMode() {
        if (!isActive) return
        isActive = false

        coroutineScope {
            // Restore brightness
            launch {
                dimAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = FADE_DURATION_MS)
                )
            }

            // Show UI
            launch {
                uiVisibility.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = FADE_DURATION_MS)
                )
            }

            // Reset artwork position
            launch {
                artworkOffsetX.animateTo(0f, animationSpec = tween(durationMillis = FADE_DURATION_MS))
            }
            launch {
                artworkOffsetY.animateTo(0f, animationSpec = tween(durationMillis = FADE_DURATION_MS))
            }
        }
    }

    suspend fun onInteraction(settings: OledSettings) {
        if (!isActive) return

        // Temporarily show UI
        coroutineScope {
            // Show UI
            launch {
                uiVisibility.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 200)
                )
            }

            // Restore some brightness temporarily
            launch {
                dimAlpha.animateTo(
                    targetValue = INTERACTION_ALPHA,
                    animationSpec = tween(durationMillis = 200)
                )
            }
        }

        // After a delay, fade back out (handled by caller with delay)
    }

    suspend fun fadeBackAfterInteraction(settings: OledSettings) {
        if (!isActive) return

        coroutineScope {
            // Dim again
            launch {
                dimAlpha.animateTo(
                    targetValue = DIMMED_ALPHA,
                    animationSpec = tween(durationMillis = FADE_DURATION_MS)
                )
            }

            // Fade out UI if enabled
            if (settings.fadeEnabled) {
                launch {
                    uiVisibility.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = FADE_DURATION_MS)
                    )
                }
            }
        }
    }

    suspend fun animateDrift(maxOffsetX: Float, maxOffsetY: Float) {
        while (true) {
            val targetX = Random.nextFloat() * maxOffsetX * 2 - maxOffsetX
            val targetY = Random.nextFloat() * maxOffsetY * 2 - maxOffsetY

            coroutineScope {
                launch {
                    artworkOffsetX.animateTo(
                        targetValue = targetX,
                        animationSpec = tween(
                            durationMillis = DRIFT_DURATION_MS,
                            easing = LinearEasing
                        )
                    )
                }
                launch {
                    artworkOffsetY.animateTo(
                        targetValue = targetY,
                        animationSpec = tween(
                            durationMillis = DRIFT_DURATION_MS,
                            easing = LinearEasing
                        )
                    )
                }
            }
        }
    }

    suspend fun animateColorCycle() {
        while (true) {
            waveformHue.animateTo(
                targetValue = 360f,
                animationSpec = tween(
                    durationMillis = COLOR_CYCLE_DURATION_MS,
                    easing = LinearEasing
                )
            )
            waveformHue.snapTo(0f)
        }
    }

    companion object {
        const val DIMMED_ALPHA = 0.3f
        const val INTERACTION_ALPHA = 0.7f
        const val FADE_DURATION_MS = 1000
        const val INTERACTION_SHOW_DURATION_MS = 3000
        const val DRIFT_DURATION_MS = 25000 // 25 seconds per movement
        const val COLOR_CYCLE_DURATION_MS = 60000 // 60 seconds for full cycle
        const val MAX_DRIFT_X_DP = 40f
        const val MAX_DRIFT_Y_DP = 30f
    }
}

@Composable
fun rememberOledModeController(): OledModeController {
    return remember { OledModeController() }
}
