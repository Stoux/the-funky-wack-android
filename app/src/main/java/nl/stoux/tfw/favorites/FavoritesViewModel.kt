package nl.stoux.tfw.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.repository.FavoritesRepository
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val queueManager: QueueManager,
) : ViewModel() {

    val favorites: StateFlow<List<LivesetWithDetails>> = favoritesRepository.favoritesList

    init {
        viewModelScope.launch { favoritesRepository.refresh() }
    }

    fun toggleFavorite(livesetId: Long) {
        viewModelScope.launch {
            runCatching { favoritesRepository.toggleFavorite(livesetId) }
        }
    }

    /** Play a favourite with the favourites list as the queue context. */
    fun playFromFavorites(livesetId: Long) {
        queueManager.setContextFromFavorites(
            startLivesetId = livesetId,
            startPositionMs = 0L,
            autoplay = true,
        )
    }
}
