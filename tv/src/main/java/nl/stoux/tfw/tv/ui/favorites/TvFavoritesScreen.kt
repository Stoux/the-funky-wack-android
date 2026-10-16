package nl.stoux.tfw.tv.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.repository.FavoritesRepository
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import nl.stoux.tfw.tv.ui.browse.LivesetListItem
import nl.stoux.tfw.tv.ui.common.TvEmptyState
import nl.stoux.tfw.tv.ui.common.TvScreenScaffold
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

    TvScreenScaffold(
        title = "Favorites",
        subtitle = if (favorites.isEmpty()) null else "${favorites.size} set${if (favorites.size == 1) "" else "s"}",
        onBack = onBack,
    ) {
        if (favorites.isEmpty()) {
            TvEmptyState("No favorites yet. Favorite a set on your phone or the website.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 48.dp),
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
    }
}
