package nl.stoux.tfw.tv.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.network.dto.auth.PlaylistDto
import nl.stoux.tfw.core.common.network.dto.auth.PlaylistItemDto
import nl.stoux.tfw.core.common.repository.EditionRepository
import nl.stoux.tfw.core.common.repository.PlaylistRepository
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import nl.stoux.tfw.tv.ui.browse.LivesetListItem
import javax.inject.Inject

/** Either a fully-cached liveset (with edition + tracks) or just the playlist preview. */
sealed class PlaylistRow {
    abstract val item: PlaylistItemDto
    data class Resolved(override val item: PlaylistItemDto, val lwd: LivesetWithDetails) : PlaylistRow()
    data class PreviewOnly(override val item: PlaylistItemDto) : PlaylistRow()
}

@HiltViewModel
class TvPlaylistDetailViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val editionRepository: EditionRepository,
    private val queueManager: QueueManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _playlist = MutableStateFlow<PlaylistDto?>(null)
    val playlist: StateFlow<PlaylistDto?> = _playlist.asStateFlow()

    /** Playlist items resolved against the cached catalog where possible. */
    val rows: StateFlow<List<PlaylistRow>> = combine(
        _playlist,
        editionRepository.cachedLivesets,
    ) { p, _ ->
        p?.items.orEmpty().map { item ->
            val lwd = editionRepository.getCachedLiveset(item.livesetId)
            if (lwd != null) PlaylistRow.Resolved(item, lwd) else PlaylistRow.PreviewOnly(item)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val shareCode = savedStateHandle.get<String>("shareCode")
        if (shareCode != null) {
            viewModelScope.launch {
                runCatching { _playlist.value = playlistRepository.getPlaylist(shareCode) }
            }
        }
    }

    fun playFrom(shareCode: String, livesetId: Long) {
        queueManager.setContextFromPlaylist(
            shareCode = shareCode,
            startLivesetId = livesetId,
            startPositionMs = 0L,
            autoplay = true,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlaylistDetailScreen(
    onPlay: () -> Unit,
    onBack: () -> Unit,
    viewModel: TvPlaylistDetailViewModel = hiltViewModel(),
) {
    val playlist by viewModel.playlist.collectAsState()
    val rows by viewModel.rows.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        val p = playlist
        if (p == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…", style = MaterialTheme.typography.bodyLarge)
            }
            return
        }

        Text(p.name, style = MaterialTheme.typography.displaySmall)
        p.description?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(24.dp))

        if (rows.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No items in this playlist.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(rows, key = { it.item.id }) { row ->
                    when (row) {
                        is PlaylistRow.Resolved -> LivesetListItem(
                            liveset = row.lwd,
                            onClick = {
                                viewModel.playFrom(p.shareCode, row.item.livesetId)
                                onPlay()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        is PlaylistRow.PreviewOnly -> PlaylistPreviewRow(
                            item = row.item,
                            onClick = {
                                viewModel.playFrom(p.shareCode, row.item.livesetId)
                                onPlay()
                            },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaylistPreviewRow(
    item: PlaylistItemDto,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val title = item.liveset?.title ?: "Liveset #${item.livesetId}"
            val artist = item.liveset?.artistName
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (artist != null) {
                    Text(artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
