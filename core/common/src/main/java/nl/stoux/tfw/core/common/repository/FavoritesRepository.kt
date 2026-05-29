package nl.stoux.tfw.core.common.repository

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.database.dao.FavoriteDao
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.database.entity.FavoriteEntity
import nl.stoux.tfw.core.common.network.ApiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Favorites with optimistic toggle and stale-while-revalidate caching.
 *
 * - DAO is the source of truth for what the UI displays.
 * - refresh() pulls the server list and replaces the cache.
 * - toggle() writes locally first, then hits the API and reverts on failure.
 *
 * Offline: previously-cached favorites still appear.
 */
@Singleton
class FavoritesRepository @Inject constructor(
    private val api: ApiService,
    private val authRepository: AuthRepository,
    private val editionRepository: EditionRepository,
    private val favoriteDao: FavoriteDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Liveset IDs with a toggle currently in flight. A second tap on the same
     * favourite while the first is still pending is silently dropped, so the
     * UI heart can't flicker between two competing optimistic writes.
     */
    private val inFlight = ConcurrentHashMap.newKeySet<Long>()

    val favoriteLivesetIds: StateFlow<List<Long>> = favoriteDao.observeIds()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val favoritesList: StateFlow<List<LivesetWithDetails>> = favoriteDao.observeIds()
        .map { ids -> ids.mapNotNull { editionRepository.getCachedLiveset(it) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Refresh favorites from server and replace the local cache. */
    suspend fun refresh() {
        if (!authRepository.isLoggedIn.value) return
        try {
            val response = api.getFavorites()
            val entities = response.favorites.map { fav ->
                FavoriteEntity(livesetId = fav.id, favoritedAt = fav.favoritedAt)
            }
            favoriteDao.replaceAll(entities)
        } catch (e: Exception) {
            Log.w("FavoritesRepository", "Failed to refresh favorites", e)
        }
    }

    fun isFavorited(livesetId: Long): Boolean = livesetId in favoriteLivesetIds.value

    /** Toggle favorite status. Optimistic update via DAO, reverts on error. */
    suspend fun toggleFavorite(livesetId: Long): Boolean? {
        // Drop overlapping taps for the same liveset — see [inFlight] for why.
        if (!inFlight.add(livesetId)) {
            Log.d("FavoritesRepository", "Toggle for $livesetId already in flight; ignoring")
            return null
        }

        try {
            val wasFavorited = isFavorited(livesetId)
            if (wasFavorited) {
                favoriteDao.delete(livesetId)
            } else {
                favoriteDao.upsert(FavoriteEntity(livesetId = livesetId))
            }

            try {
                if (wasFavorited) {
                    api.removeFavorite(livesetId)
                } else {
                    api.addFavorite(livesetId)
                }
                return !wasFavorited
            } catch (e: Exception) {
                Log.w("FavoritesRepository", "Failed to toggle favorite, reverting", e)
                if (wasFavorited) {
                    favoriteDao.upsert(FavoriteEntity(livesetId = livesetId))
                } else {
                    favoriteDao.delete(livesetId)
                }
                throw e
            }
        } finally {
            inFlight.remove(livesetId)
        }
    }
}
