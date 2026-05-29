package nl.stoux.tfw.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.network.dto.auth.PlaylistDto
import nl.stoux.tfw.core.common.repository.PlaylistRepository
import javax.inject.Inject

@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val ownPlaylists: StateFlow<List<PlaylistDto>> = playlistRepository.playlists
        .map { all -> all.filter { it.isOwn } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init { viewModelScope.launch { playlistRepository.refresh() } }

    fun addLivesetTo(shareCode: String, livesetId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { playlistRepository.addItem(shareCode, livesetId) }
            _busy.value = false
            onDone()
        }
    }

    fun createAndAdd(name: String, livesetId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val created = runCatching { playlistRepository.createPlaylist(name, description = null) }.getOrNull()
            if (created != null) {
                runCatching { playlistRepository.addItem(created.shareCode, livesetId) }
            }
            _busy.value = false
            onDone()
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    livesetId: Long,
    onDismiss: () -> Unit,
    viewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val playlists by viewModel.ownPlaylists.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (showCreate) "New playlist" else "Add to playlist") },
        text = {
            if (showCreate) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (playlists.isEmpty()) {
                Text("You don't have any playlists yet. Create one to add this set.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(playlists, key = { it.shareCode }) { pl ->
                        TextButton(
                            onClick = {
                                viewModel.addLivesetTo(pl.shareCode, livesetId, onDismiss)
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(pl.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${pl.itemsCount} item${if (pl.itemsCount == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showCreate) {
                Button(
                    onClick = {
                        viewModel.createAndAdd(newName.trim(), livesetId, onDismiss)
                    },
                    enabled = !busy && newName.isNotBlank(),
                ) { Text("Create & add") }
            } else {
                TextButton(onClick = { showCreate = true }, enabled = !busy) {
                    Text("New playlist")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
