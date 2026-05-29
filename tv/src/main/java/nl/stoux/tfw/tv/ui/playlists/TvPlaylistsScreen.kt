package nl.stoux.tfw.tv.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        Text("Playlists", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(24.dp))

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "No playlists yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    Button(onClick = { onPlaylistClick(playlist.shareCode) }) {
                        Text("${playlist.name} (${playlist.itemsCount} items)")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}
