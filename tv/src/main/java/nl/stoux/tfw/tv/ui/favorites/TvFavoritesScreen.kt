package nl.stoux.tfw.tv.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.repository.FavoritesRepository
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import nl.stoux.tfw.tv.ui.browse.LivesetListItem
import javax.inject.Inject

@HiltViewModel
class TvFavoritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val queueManager: QueueManager,
) : ViewModel() {
    val favorites: StateFlow<List<LivesetWithDetails>> = favoritesRepository.favoritesList
    init {
        viewModelScope.launch { favoritesRepository.refresh() }
    }

    fun playLiveset(livesetId: Long) {
        queueManager.setContextFromFavorites(
            startLivesetId = livesetId,
            startPositionMs = 0L,
            autoplay = true,
        )
    }
}

@Composable
fun TvFavoritesScreen(
    onLivesetClick: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: TvFavoritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        Text("Favorites", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(24.dp))

        if (favorites.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "No favorites yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(favorites, key = { it.liveset.id }) { lwd ->
                    LivesetListItem(
                        liveset = lwd,
                        onClick = {
                            viewModel.playLiveset(lwd.liveset.id)
                            onLivesetClick(lwd.liveset.id)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}
