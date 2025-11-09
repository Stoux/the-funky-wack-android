package nl.stoux.tfw.feature.browser

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.wrapContentSize
import coil.compose.AsyncImage
import nl.stoux.tfw.core.common.database.dao.EditionWithContent
import nl.stoux.tfw.core.common.database.entity.artworkUrl
import nl.stoux.tfw.feature.player.util.formatTime
import nl.stoux.tfw.feature.player.util.shareLiveset
import nl.stoux.tfw.service.playback.service.session.CustomMediaId

@Composable
fun EditionListScreen(
    modifier: Modifier = Modifier,
    viewModel: EditionListViewModel,

    onPlayClicked: (mediaId: CustomMediaId) -> Unit,
    onOpenPlayer: () -> Unit,
    onAddToQueue: (livesetId: Long) -> Unit,
) {
    val editions by viewModel.editions.collectAsState()
    EditionList(
        editions = editions,
        onPlayClicked = onPlayClicked,
        onOpenPlayer = onOpenPlayer,
        onAddToQueue = onAddToQueue,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditionList(
    editions: List<EditionWithContent>,
    onPlayClicked: (mediaId: CustomMediaId) -> Unit,
    onOpenPlayer: () -> Unit,
    onAddToQueue: (livesetId: Long) -> Unit,
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
                        posterUrl = edition.edition.artworkUrl,
                        fullPosterUrl = edition.edition.posterUrl,
                    )
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
            items(edition.livesets, key = { it.liveset.id }) { lwd ->
                LivesetRow(
                    livesetId = lwd.liveset.id,
                    title = lwd.liveset.title,
                    artist = lwd.liveset.artistName,
                    genre = lwd.liveset.genre,
                    bpm = lwd.liveset.bpm,
                    durationSec = lwd.liveset.durationSeconds,
                    // TODO: This resolve URL logic should be moved to the model and/or a service which provides the correct URL based on the preferred quality.
                    isPlayable = listOfNotNull(lwd.liveset.losslessUrl, lwd.liveset.hqUrl, lwd.liveset.lqUrl).isNotEmpty(),
                    onPlay = {
                        val mediaId = CustomMediaId.forEntity(lwd.liveset)
                        val url = listOfNotNull(lwd.liveset.losslessUrl, lwd.liveset.hqUrl, lwd.liveset.lqUrl).firstOrNull()
                        if (url != null) {
                            onPlayClicked(mediaId)
                            onOpenPlayer()
                        }
                    },
                    onAddToQueueClicked = { onAddToQueue(lwd.liveset.id) },
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
    fullPosterUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val showPoster = remember { mutableStateOf(false) }
    val largePosterUrl = fullPosterUrl ?: posterUrl

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left: text content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = 16.dp,
                    end = if (posterUrl == null) 16.dp else 8.dp
                )
        ) {
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
        if (posterUrl != null) {
            AsyncImage(
                model = posterUrl,
                contentDescription = "Edition poster",
                modifier = Modifier
                    .height(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .fillMaxWidth(0.18f)
                    .clickable { showPoster.value = true },
            )
        }
    }

    if (showPoster.value && largePosterUrl != null) {
        Dialog(onDismissRequest = { showPoster.value = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showPoster.value = false },
                contentAlignment = Alignment.Center
            ) {
                // Zoomable image that fits screen by default and supports pinch-to-zoom
                ZoomablePosterImage(
                    url = largePosterUrl,
                    contentDescription = "Edition poster full size",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ZoomablePosterImage(
    url: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val scaleState = remember { mutableStateOf(1f) }
    val offsetXState = remember { mutableStateOf(0f) }
    val offsetYState = remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val oldScale = scaleState.value
                    val newScale = (oldScale * zoom).coerceIn(1f, 5f)
                    scaleState.value = newScale

                    if (newScale <= 1f) {
                        // Reset pan when back to base scale
                        offsetXState.value = 0f
                        offsetYState.value = 0f
                    } else {
                        offsetXState.value += pan.x
                        offsetYState.value += pan.y
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scaleState.value,
                    scaleY = scaleState.value,
                    translationX = offsetXState.value,
                    translationY = offsetYState.value,
                )
        )
    }
}

@Composable
private fun LivesetRow(
    livesetId: Long,
    title: String,
    artist: String,
    genre: String?,
    bpm: String?,
    durationSec: Int?,
    isPlayable: Boolean,
    onPlay: () -> Unit,
    onAddToQueueClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context: Context = LocalContext.current
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
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isPlayable) 0.5f else 0.2f))
                .then(
                    if (isPlayable) Modifier
                        .padding(0.dp)
                        .clickable { onPlay() } else Modifier.alpha(0f)
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

        // Right: duration + overflow menu
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (durationSec != null) {
                Text(
                    text = formatTime(durationSec * 1000L),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val menuExpanded = remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded.value = true }) {
                    // Use simple text glyph to avoid adding icon deps
                    Text("⋮")
                }
                DropdownMenu(
                    expanded = menuExpanded.value,
                    onDismissRequest = { menuExpanded.value = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to Queue") },
                        onClick = {
                            onAddToQueueClicked()
                            menuExpanded.value = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            shareLiveset(context, livesetId)
                            menuExpanded.value = false
                        }
                    )
                }
            }
        }
    }
}