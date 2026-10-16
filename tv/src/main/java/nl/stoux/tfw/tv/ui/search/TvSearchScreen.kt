package nl.stoux.tfw.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.stoux.tfw.core.common.network.dto.SearchResponse
import nl.stoux.tfw.core.common.network.dto.TrackAppearanceDto
import nl.stoux.tfw.core.common.network.dto.trackDisplayName
import nl.stoux.tfw.tv.ui.common.TvEmptyState
import nl.stoux.tfw.tv.ui.common.TvHeaderButton
import nl.stoux.tfw.tv.ui.common.TvListCard
import nl.stoux.tfw.tv.ui.common.TvScreenScaffold
import nl.stoux.tfw.tv.ui.common.requestFocusWhenAttached

@Composable
fun TvSearchScreen(
    onPlay: () -> Unit,
    onBack: () -> Unit,
    viewModel: TvSearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val state by viewModel.state.collectAsState()

    val fieldFocusRequester = remember { FocusRequester() }
    // Result rows by key, so back from NowPlaying lands on the row that was played
    val rowFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requesterFor(key: String) = rowFocusRequesters.getOrPut(key) { FocusRequester() }
    var returnFocusTo by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedTracks by rememberSaveable { mutableStateOf(listOf<String>()) }
    val listState = rememberLazyListState()

    val fieldState = rememberTextFieldState(initialText = query)
    var keyboardWanted by remember { mutableStateOf(false) }
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }.collect(viewModel::onQueryChange)
    }

    LaunchedEffect(Unit) {
        val key = returnFocusTo
        returnFocusTo = null
        if (key == null || !requestFocusWhenAttached(requesterFor(key))) {
            // Fresh screen: straight into typing. With a query already there, let the user pick.
            if (requestFocusWhenAttached(fieldFocusRequester) && fieldState.text.isEmpty()) {
                keyboardWanted = true
            }
        }
    }

    val play: (String, Long, Int?) -> Unit = { key, livesetId, timestampSec ->
        returnFocusTo = key
        viewModel.play(livesetId, timestampSec)
        onPlay()
    }

    TvScreenScaffold(
        title = "Search",
        subtitle = subtitleFor(state),
        onBack = onBack,
    ) {
        TvSearchField(
            state = fieldState,
            keyboardWanted = keyboardWanted,
            onKeyboardWantedChange = { keyboardWanted = it },
            onSearch = viewModel::submit,
            modifier = Modifier.focusRequester(fieldFocusRequester),
        )
        Spacer(modifier = Modifier.height(24.dp))

        val results = state.results
        when {
            state.status == TvSearchUiState.Status.Error -> TvSearchError(onRetry = viewModel::submit)
            state.status == TvSearchUiState.Status.Idle ->
                TvEmptyState("Search sets, tracks and tracklists (at least 2 characters)")
            results == null -> TvEmptyState("Searching...")
            state.isEmptyResult && state.status == TvSearchUiState.Status.Results ->
                TvEmptyState("Nothing found for \"${state.resultsQuery}\"")
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                searchResults(
                    results = results,
                    expandedTracks = expandedTracks,
                    onToggleTrack = { slug ->
                        expandedTracks = if (slug in expandedTracks) expandedTracks - slug else expandedTracks + slug
                    },
                    requesterFor = ::requesterFor,
                    onPlay = play,
                )
            }
        }
    }
}

private fun subtitleFor(state: TvSearchUiState): String? {
    val results = state.results
    return when {
        state.status == TvSearchUiState.Status.Loading -> "Searching..."
        state.status != TvSearchUiState.Status.Results || results == null || state.isEmptyResult -> null
        else -> listOfNotNull(
            results.livesets.size.takeIf { it > 0 }?.let { plural(it, "set", "sets") },
            results.tracks.size.takeIf { it > 0 }?.let { plural(it, "track", "tracks") },
            results.unlinkedLines.size.takeIf { it > 0 }?.let { plural(it, "tracklist line", "tracklist lines") },
        ).joinToString(" · ")
    }
}

private fun LazyListScope.searchResults(
    results: SearchResponse,
    expandedTracks: List<String>,
    onToggleTrack: (String) -> Unit,
    requesterFor: (String) -> FocusRequester,
    onPlay: (key: String, livesetId: Long, timestampSec: Int?) -> Unit,
) {
    if (results.livesets.isNotEmpty()) {
        item(key = "header:sets") { SectionHeader("Sets") }
        results.livesets.forEach { set ->
            val key = "set:${set.id}"
            item(key = key) {
                ResultRow(
                    title = set.title,
                    subtitle = listOfNotNull(
                        set.artistName,
                        set.edition?.let { ed -> listOfNotNull("TFW #${ed.number}", ed.tagLine).joinToString(" ") },
                        set.genre,
                    ).joinToString(" · "),
                    onClick = { onPlay(key, set.id, null) },
                    modifier = Modifier.focusRequester(requesterFor(key)),
                )
            }
        }
    }

    if (results.tracks.isNotEmpty()) {
        item(key = "header:tracks") { SectionHeader("Tracks") }
        results.tracks.forEachIndexed { trackIndex, track ->
            val trackKey = "track:$trackIndex:${track.slug}"
            val expanded = track.slug in expandedTracks
            val setCount = track.appearances.distinctBy { it.livesetId }.size
            item(key = trackKey) {
                ResultRow(
                    title = trackDisplayName(track.artist, track.title, track.version),
                    subtitle = if (setCount == 0) "Not in any set" else "Played in ${plural(setCount, "set", "sets")}",
                    onClick = { onToggleTrack(track.slug) },
                    modifier = Modifier.focusRequester(requesterFor(trackKey)),
                    trailing = {
                        if (track.appearances.isNotEmpty()) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) "Hide sets" else "Show sets",
                            )
                        }
                    },
                )
            }
            if (expanded) {
                track.appearances.forEachIndexed { index, appearance ->
                    val key = "$trackKey:appearance:$index"
                    item(key = key) {
                        ResultRow(
                            title = appearance.livesetTitle,
                            subtitle = appearanceSubtitle(appearance),
                            onClick = { onPlay(key, appearance.livesetId, appearance.timestamp) },
                            modifier = Modifier
                                .padding(start = 48.dp)
                                .focusRequester(requesterFor(key)),
                        )
                    }
                }
            }
        }
    }

    if (results.unlinkedLines.isNotEmpty()) {
        item(key = "header:lines") { SectionHeader("Tracklist lines") }
        results.unlinkedLines.forEachIndexed { index, line ->
            val key = "line:$index:${line.livesetId}"
            item(key = key) {
                ResultRow(
                    title = line.line,
                    subtitle = listOfNotNull(
                        line.livesetTitle,
                        line.artistName,
                        line.editionNumber?.let { "TFW #$it" },
                        line.timestamp?.let { formatTimestamp(it) },
                    ).joinToString(" · "),
                    onClick = { onPlay(key, line.livesetId, line.timestamp) },
                    modifier = Modifier.focusRequester(requesterFor(key)),
                )
            }
        }
    }
}

private fun appearanceSubtitle(appearance: TrackAppearanceDto): String = listOfNotNull(
    appearance.artistName,
    appearance.editionNumber?.let { "TFW #$it" },
    appearance.editionDate,
    appearance.timestamp?.let { formatTimestamp(it) } ?: "from the start",
).joinToString(" · ")

/**
 * Single-line field for the TV on-screen keyboard. Select opens the keyboard, IME Search submits,
 * and D-pad up/down always leaves the field so the results and the Back button stay reachable
 * (left/right keep moving the cursor).
 *
 * Focus alone mustn't pop the keyboard: it grabs the D-pad, so moving past the field would get
 * stuck. Only the state-based BasicTextField honours [KeyboardOptions.showKeyboardOnFocus], and it
 * restarts the input session when the options change while focused, so [keyboardWanted] flipping
 * to true is what actually opens the keyboard.
 */
@Composable
private fun TvSearchField(
    state: TextFieldState,
    keyboardWanted: Boolean,
    onKeyboardWantedChange: (Boolean) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)

    BasicTextField(
        state = state,
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = MaterialTheme.typography.titleLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = keyboardWanted),
        onKeyboardAction = {
            keyboardController?.hide()
            onSearch()
        },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) onKeyboardWantedChange(false)
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        keyboardController?.hide()
                        focusManager.moveFocus(FocusDirection.Down)
                    }
                    Key.DirectionUp -> {
                        keyboardController?.hide()
                        focusManager.moveFocus(FocusDirection.Up)
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        // Session already running (keyboard was closed with Back): just show it again
                        if (keyboardWanted) keyboardController?.show() else onKeyboardWantedChange(true)
                        true
                    }
                    else -> false
                }
            },
        decorator = { innerTextField ->
            Row(
                modifier = Modifier
                    .background(colors.surfaceVariant.copy(alpha = if (focused) 0.9f else 0.6f), shape)
                    .border(2.dp, if (focused) colors.primary else colors.surfaceVariant, shape)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = colors.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (state.text.isEmpty()) {
                        Text(
                            text = "Sets, DJs, tracks...",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.onSurface.copy(alpha = 0.4f),
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
private fun ColumnScope.TvSearchError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Search didn't work. Check your connection and try again.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        TvHeaderButton(text = "Retry", onClick = onRetry)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ResultRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    TvListCard(onClick = onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing()
        }
    }
}

private fun plural(count: Int, one: String, many: String) = "$count ${if (count == 1) one else many}"

private fun formatTimestamp(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
