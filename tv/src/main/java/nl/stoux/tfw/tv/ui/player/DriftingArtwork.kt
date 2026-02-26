package nl.stoux.tfw.tv.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun DriftingArtwork(
    imageUrl: String?,
    contentDescription: String?,
    isDrifting: Boolean,
    modifier: Modifier = Modifier,
    maxDriftX: Float = 40f,
    maxDriftY: Float = 30f,
) {
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    LaunchedEffect(isDrifting) {
        if (isDrifting) {
            // Continuous slow drift animation
            while (true) {
                val targetX = Random.nextFloat() * maxDriftX * 2 - maxDriftX
                val targetY = Random.nextFloat() * maxDriftY * 2 - maxDriftY

                // Animate to random position within bounds
                // Speed: ~20-30 seconds per movement
                coroutineScope {
                    launch {
                        offsetX.animateTo(
                            targetValue = targetX,
                            animationSpec = tween(
                                durationMillis = 25000,
                                easing = LinearEasing
                            )
                        )
                    }
                    launch {
                        offsetY.animateTo(
                            targetValue = targetY,
                            animationSpec = tween(
                                durationMillis = 25000,
                                easing = LinearEasing
                            )
                        )
                    }
                }
            }
        } else {
            // Reset to center when not drifting
            coroutineScope {
                launch {
                    offsetX.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 500)
                    )
                }
                launch {
                    offsetY.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 500)
                    )
                }
            }
        }
    }

    AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier.offset {
            IntOffset(
                x = offsetX.value.dp.toPx().roundToInt(),
                y = offsetY.value.dp.toPx().roundToInt()
            )
        },
        contentScale = ContentScale.Fit
    )
}
