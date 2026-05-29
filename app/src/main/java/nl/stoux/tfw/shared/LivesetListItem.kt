package nl.stoux.tfw.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.feature.player.util.formatTime

/**
 * Shared list-item visual for livesets that lives outside the edition browse.
 * Same shape as `EditionListScreen.LivesetRow` (play button + title + meta) but
 * shows the source edition number underneath and accepts a custom trailing
 * action slot per call site.
 */
@Composable
fun LivesetListItem(
    lwd: LivesetWithDetails,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val liveset = lwd.liveset
    val isPlayable = listOfNotNull(liveset.losslessUrl, liveset.hqUrl, liveset.lqUrl).isNotEmpty()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = isPlayable, onClick = onPlay)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isPlayable) 0.5f else 0.2f)
                )
                .then(if (isPlayable) Modifier else Modifier.alpha(0.4f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("▶", color = if (isPlayable) MaterialTheme.colorScheme.onBackground else Color.Gray)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = liveset.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${liveset.artistName} · TFW #${lwd.edition.number}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(liveset.genre, liveset.bpm?.let { "$it BPM" }).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            liveset.durationSeconds?.let { sec ->
                Text(
                    formatTime(sec * 1000L),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.invoke()
        }
    }
}
