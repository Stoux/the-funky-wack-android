package nl.stoux.tfw.core.common.repository

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.network.ApiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages favorites with optimistic toggle and server sync.
 */
@Singleton
class FavoritesRepository @Inject constructor(
    private val api: ApiService,
    private val authRepository: AuthRepository,
    private val editionRepository: EditionRepository,
) {
    private val _favorites = MutableStateFlow<List<Long>>(emptyList())
    val favoriteLivesetIds: StateFlow<List<Long>> = _favorites.asStateFlow()

    private val _favoritesList = MutableStateFlow<List<LivesetWithDetails>>(emptyList())
    val favoritesList: StateFlow<List<LivesetWithDetails>> = _favoritesList.asStateFlow()

    /**
     * Refresh favorites from server.
     */
    suspend fun refresh() {
        if (!authRepository.isLoggedIn.value) return
        try {
            val response = api.getFavorites()
            val ids = response.favorites.map { it.id }
            _favorites.value = ids
            // Resolve to LivesetWithDetails from cache
            _favoritesList.value = ids.mapNotNull { editionRepository.getCachedLiveset(it) }
        } catch (e: Exception) {
            Log.w("FavoritesRepository", "Failed to refresh favorites", e)
        }
    }

    /**
     * Check if a liveset is favorited (from local cache).
     */
    fun isFavorited(livesetId: Long): Boolean = livesetId in _favorites.value

    /**
     * Toggle favorite status. Optimistic update, reverts on error.
     */
    suspend fun toggleFavorite(livesetId: Long): Boolean {
        val wasFavorited = isFavorited(livesetId)
        // Optimistic update
        if (wasFavorited) {
            _favorites.value = _favorites.value - livesetId
        } else {
            _favorites.value = _favorites.value + livesetId
        }

        try {
            if (wasFavorited) {
                api.removeFavorite(livesetId)
            } else {
                api.addFavorite(livesetId)
            }
            // Update list
            _favoritesList.value = _favorites.value.mapNotNull { editionRepository.getCachedLiveset(it) }
            return !wasFavorited
        } catch (e: Exception) {
            Log.w("FavoritesRepository", "Failed to toggle favorite", e)
            // Revert optimistic update
            if (wasFavorited) {
                _favorites.value = _favorites.value + livesetId
            } else {
                _favorites.value = _favorites.value - livesetId
            }
            _favoritesList.value = _favorites.value.mapNotNull { editionRepository.getCachedLiveset(it) }
            throw e
        }
    }
}
