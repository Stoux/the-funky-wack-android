package nl.stoux.tfw.tracks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import nl.stoux.tfw.core.common.network.dto.TrackAppearanceDto
import nl.stoux.tfw.feature.player.util.formatTime
import retrofit2.HttpException
import java.io.IOException

/** "Played in 3 sets", null when it wasn't played anywhere (yet). */
fun playedInLabel(appearances: List<TrackAppearanceDto>): String? {
    val sets = appearances.distinctBy { it.livesetId }.size
    return when (sets) {
        0 -> null
        1 -> "Played in 1 set"
        else -> "Played in $sets sets"
    }
}

/** "mm:ss" for a timestamp in seconds, null when the line has no time. */
fun formatTimestamp(seconds: Int?): String? = seconds?.let { formatTime(it * 1000L) }

/** "TFW #12", null without an edition number. */
fun editionLabel(number: String?): String? = number?.takeIf { it.isNotBlank() }?.let { "TFW #$it" }

fun isNotFound(e: Throwable): Boolean = (e as? HttpException)?.code() == 404

fun errorMessage(e: Throwable, fallback: String): String = when {
    (e as? HttpException)?.code() == 429 -> "Slow down a bit, try again in a moment."
    e is IOException -> "Couldn't reach the server. Check your connection."
    else -> fallback
}

/** Round play pill, same look as the liveset rows. */
@Composable
fun PlayPill(modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.5f else 0.2f))
            .padding(8.dp),
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp),
        )
    }
}
