package nl.stoux.tfw.tv.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.stoux.tfw.tv.ui.common.TvEmptyState
import nl.stoux.tfw.tv.ui.common.TvListCard
import nl.stoux.tfw.tv.ui.common.TvScreenScaffold
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.network.dto.auth.PlaylistDto
import nl.stoux.tfw.core.common.repository.PlaylistRepository
import javax.inject.Inject

@HiltViewModel
class TvPlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {
    val playlists: StateFlow<List<PlaylistDto>> = playlistRepository.playlists
    init {
        viewModelScope.launch { playlistRepository.refresh() }
    }
}

@Composable
fun TvPlaylistsScreen(
    onPlaylistClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: TvPlaylistsViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsState()

    TvScreenScaffold(title = "Playlists", onBack = onBack) {
        if (playlists.isEmpty()) {
            TvEmptyState("No playlists yet. Create one on your phone or the website.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    TvListCard(onClick = { onPlaylistClick(playlist.shareCode) }) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${playlist.itemsCount} set${if (playlist.itemsCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
