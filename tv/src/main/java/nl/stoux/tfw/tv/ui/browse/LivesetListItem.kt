package nl.stoux.tfw.tv.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LivesetListItem(
    liveset: LivesetWithDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onUpPressed: (() -> Unit)? = null,
) {
    val entity = liveset.liveset

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(120.dp)
            .then(
                if (onUpPressed != null) {
                    Modifier.onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                            onUpPressed()
                            false // Let the event propagate (don't consume)
                        } else false
                    }
                } else Modifier
            ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            // Top: Title (2 lines)
            Text(
                text = entity.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.weight(1f))

            // Artist (above bullet points)
            Text(
                text = entity.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Bottom: [timeslot] • #[order] • genre • BPM • duration
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val orderNumber = entity.lineupOrder?.let { "#$it" }

                val detailParts = buildList {
                    entity.timeslotRaw?.takeIf { it != "INVALID" && it.isNotBlank() }?.let { add(it) }
                    orderNumber?.let { add(it) }
                    entity.genre?.let { add(it) }
                    entity.bpm?.let { add("$it BPM") }
                    entity.durationSeconds?.let { add(formatDuration(it)) }
                }

                detailParts.forEachIndexed { index, part ->
                    if (index > 0) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    // Make order number brighter
                    val isOrderNumber = part == orderNumber
                    Text(
                        text = part,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOrderNumber)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}
