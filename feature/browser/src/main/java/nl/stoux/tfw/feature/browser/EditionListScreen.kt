package nl.stoux.tfw.feature.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.stoux.tfw.core.common.database.dao.EditionWithContent

@Composable
fun EditionListScreen(
    modifier: Modifier = Modifier,
    viewModel: EditionListViewModel,
    onPlayClicked: (url: String, title: String?, artist: String?) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val editions by viewModel.editions.collectAsState()
    EditionList(
        editions = editions,
        onPlayClicked = onPlayClicked,
        onOpenPlayer = onOpenPlayer,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditionList(
    editions: List<EditionWithContent>,
    onPlayClicked: (url: String, title: String?, artist: String?) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(vertical = 4.dp)) {
        editions.forEach { edition ->
            item(key = "header-${edition.edition.id}") {
                Column {
                    EditionHeader(
                        number = edition.edition.number,
                        tagLine = edition.edition.tagLine,
                        date = edition.edition.date,
                        notes = edition.edition.notes,
                        posterUrl = edition.edition.posterUrl,
                    )
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
            items(edition.livesets, key = { it.liveset.id }) { lwd ->
                LivesetRow(
                    title = lwd.liveset.title,
                    artist = lwd.liveset.artistName,
                    genre = lwd.liveset.genre,
                    bpm = lwd.liveset.bpm,
                    durationSec = lwd.liveset.durationSeconds,
                    // TODO: This resolve URL logic should be moved to the model and/or a service which provides the correct URL based on the preferred quality.
                    isPlayable = listOfNotNull(lwd.liveset.losslessUrl, lwd.liveset.hqUrl, lwd.liveset.lqUrl).isNotEmpty(),
                    onPlay = {
                        val url = listOfNotNull(lwd.liveset.losslessUrl, lwd.liveset.hqUrl, lwd.liveset.lqUrl).firstOrNull()
                        if (url != null) {
                            onPlayClicked(url, lwd.liveset.title, lwd.liveset.artistName)
                            onOpenPlayer()
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun EditionHeader(
    number: String,
    tagLine: String?,
    date: String?,
    notes: String?,
    posterUrl: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left: text content
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "TFW #$number",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!tagLine.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "- $tagLine",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!date.isNullOrBlank()) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!notes.isNullOrBlank()) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Right: poster (Coil)
        coil.compose.AsyncImage(
            model = posterUrl,
            contentDescription = "Edition poster",
            modifier = Modifier
                .height(96.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .fillMaxWidth(0.18f),
        )
    }
}

@Composable
private fun LivesetRow(
    title: String,
    artist: String,
    genre: String?,
    bpm: String?,
    durationSec: Int?,
    isPlayable: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left: play "icon" button (text-based for now to avoid extra deps)
        Box(
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isPlayable) 0.5f else 0.2f))
                .then(
                    if (isPlayable) Modifier
                        .padding(0.dp)
                        .clickable { onPlay() } else Modifier
                )
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("▶", color = if (isPlayable) MaterialTheme.colorScheme.onBackground else Color.Gray)
        }

        // Middle: text stack
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(genre, bpm?.let { "${it} BPM" }).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(text = meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Right: duration
        if (durationSec != null) {
            Text(text = formatDuration(durationSec), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
