package nl.stoux.tfw.tv.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvBrowseScreen(
    onEditionClick: (Long) -> Unit,
    onLivesetClick: (Long) -> Unit,
    onNowPlayingClick: () -> Unit,
    viewModel: TvBrowseViewModel = hiltViewModel(),
) {
    val editions by viewModel.editions.collectAsState()
    val queueState by viewModel.queueState.collectAsState()
    val currentLiveset by viewModel.currentLiveset.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val resumeState by viewModel.resumeState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (editions.isEmpty()) {
            // Loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()

            // Check if NowPlayingCard or ContinueCard is visible
            val currentItem = queueState.effective.getOrNull(queueState.currentEffectiveIndex)
            val hasNowPlaying = currentItem != null || queueState.isPlaying
            val hasTopCard = hasNowPlaying || resumeState != null

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // App title
                item {
                    Column(
                        modifier = Modifier.padding(start = 48.dp, top = 48.dp, bottom = 8.dp)
                    ) {
                        Text(
                            text = "The Funky Wack",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Wacky beats, the recordings.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }

                // Now Playing card (if something is playing/paused) OR Continue card (if resume state exists)
                item {
                    if (hasNowPlaying) {
                        NowPlayingCard(
                            queueState = queueState,
                            currentLiveset = currentLiveset,
                            currentTrack = currentTrack,
                            onClick = onNowPlayingClick
                        )
                    } else {
                        resumeState?.let { state ->
                            ContinueCard(
                                resumeState = state,
                                onClick = {
                                    viewModel.resumePlayback()
                                    onNowPlayingClick()
                                }
                            )
                        }
                    }
                }

                // Edition rows with poster + liveset list
                items(
                    items = editions,
                    key = { it.edition.id }
                ) { edition ->
                    EditionRow(
                        edition = edition,
                        onLivesetClick = { livesetId ->
                            viewModel.playLiveset(livesetId)
                            onLivesetClick(livesetId)
                        },
                        onHeaderClick = { onEditionClick(edition.edition.id) },
                        onUpPressed = if (edition == editions.firstOrNull() && !hasTopCard) {
                            { coroutineScope.launch { listState.animateScrollToItem(0) } }
                        } else null
                    )
                }

                // Bottom padding
                item {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}
