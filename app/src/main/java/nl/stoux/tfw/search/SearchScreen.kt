package nl.stoux.tfw.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import nl.stoux.tfw.core.common.network.dto.SearchLivesetDto
import nl.stoux.tfw.core.common.network.dto.SearchTrackDto
import nl.stoux.tfw.core.common.network.dto.UnlinkedLineDto
import nl.stoux.tfw.core.common.network.dto.trackDisplayName
import nl.stoux.tfw.core.common.repository.SearchRepository
import nl.stoux.tfw.tracks.PlayPill
import nl.stoux.tfw.tracks.editionLabel
import nl.stoux.tfw.tracks.formatTimestamp
import nl.stoux.tfw.tracks.playedInLabel

@Composable
fun SearchScreen(
    onOpenPlayer: () -> Unit,
    onOpenTrack: (slug: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val likedSlugs by viewModel.likedSlugs.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    // Autofocus on a fresh search; coming back from a track page with results shouldn't pop the keyboard
    LaunchedEffect(Unit) {
        if (viewModel.query.isBlank()) focusRequester.requestFocus()
    }

    val hideKeyboard = {
        keyboard?.hide()
        focusManager.clearFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = viewModel.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Sets, tracks, tracklist lines…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (viewModel.query.isNotEmpty()) {
                    IconButton(onClick = {
                        viewModel.clear()
                        focusRequester.requestFocus()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                viewModel.submit()
                hideKeyboard()
            }),
        )

        if (state.isLoading && state.results != null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val results = state.results
            val typed = viewModel.query.trim()
            when {
                // First search still in its debounce window
                state.query == null && typed.length >= SearchRepository.MIN_QUERY_LENGTH ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.query == null -> CenteredHint(
                    if (typed.isEmpty()) "Search for sets, tracks and tracklist lines."
                    else "Type at least ${SearchRepository.MIN_QUERY_LENGTH} characters."
                )
                state.isLoading && results == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        state.error ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = viewModel::retry) { Text("Retry") }
                }
                results == null -> Unit
                state.isEmpty -> CenteredHint("No results for “${state.query}”")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    section("Sets", results.livesets, key = { "set-${it.id}" }) { liveset ->
                        SetResultRow(liveset, onClick = {
                            hideKeyboard()
                            viewModel.play(liveset.id, null)
                            onOpenPlayer()
                        })
                    }
                    section("Tracks", results.tracks, key = { "track-${it.slug}" }) { track ->
                        TrackResultRow(
                            track = track,
                            isLiked = track.slug in likedSlugs,
                            onClick = {
                                hideKeyboard()
                                onOpenTrack(track.slug)
                            },
                        )
                    }
                    section("Tracklist lines", results.unlinkedLines, key = null) { line ->
                        UnlinkedLineRow(line, onClick = {
                            hideKeyboard()
                            viewModel.play(line.livesetId, line.timestamp)
                            onOpenPlayer()
                        })
                    }
                }
            }
        }
    }
}

private fun <T> LazyListScope.section(
    title: String,
    items: List<T>,
    key: ((T) -> Any)?,
    row: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "header-$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
    items(items, key = key) { row(it) }
}

@Composable
private fun CenteredHint(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultRow(
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    meta: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!meta.isNullOrBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun SetResultRow(liveset: SearchLivesetDto, onClick: () -> Unit) {
    val edition = liveset.edition
    val subtitle = listOfNotNull(
        liveset.artistName.takeIf { it.isNotBlank() },
        editionLabel(edition?.number)?.let { label ->
            edition?.tagLine?.takeIf { it.isNotBlank() }?.let { "$label: $it" } ?: label
        },
    ).joinToString(" · ")
    ResultRow(
        onClick = onClick,
        leading = { PlayPill() },
        title = liveset.title,
        subtitle = subtitle,
        meta = liveset.genre,
    )
}

@Composable
private fun TrackResultRow(track: SearchTrackDto, isLiked: Boolean, onClick: () -> Unit) {
    ResultRow(
        onClick = onClick,
        leading = {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp).size(20.dp),
            )
        },
        title = trackDisplayName(track.artist, track.title, track.version),
        subtitle = playedInLabel(track.appearances),
        trailing = if (isLiked) {
            {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Liked",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else null,
    )
}

@Composable
private fun UnlinkedLineRow(line: UnlinkedLineDto, onClick: () -> Unit) {
    ResultRow(
        onClick = onClick,
        leading = {
            Icon(
                Icons.AutoMirrored.Filled.FormatListBulleted,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp).size(20.dp),
            )
        },
        title = line.line,
        subtitle = listOfNotNull(
            line.livesetTitle.takeIf { it.isNotBlank() },
            editionLabel(line.editionNumber),
            formatTimestamp(line.timestamp),
        ).joinToString(" · "),
    )
}
