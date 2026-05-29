package nl.stoux.tfw.core.common.repository

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.database.dao.PlaybackPositionDao
import nl.stoux.tfw.core.common.database.entity.PlaybackPositionEntity
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.auth.PlaybackPositionDto
import nl.stoux.tfw.core.common.network.dto.auth.SavePositionRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Continue-listening positions with local-first writes and best-effort
 * server sync. Reads always come from Room so the resume shelf works
 * offline (e.g. start of a car trip before the network is up).
 */
@Singleton
class PlaybackPositionRepository @Inject constructor(
    private val api: ApiService,
    private val authRepository: AuthRepository,
    private val positionDao: PlaybackPositionDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val positions: StateFlow<List<PlaybackPositionDto>> = positionDao.observeAll()
        .map { rows ->
            rows.map { entity ->
                PlaybackPositionDto(
                    livesetId = entity.livesetId,
                    position = entity.positionSeconds,
                    updatedAt = entity.updatedAt,
                )
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Fetch all positions from the server and replace the local cache. */
    suspend fun refresh() {
        if (!authRepository.isLoggedIn.value) return
        try {
            val remote = api.getPlaybackPositions().positions
            positionDao.replaceAll(
                remote.map {
                    PlaybackPositionEntity(
                        livesetId = it.livesetId,
                        positionSeconds = it.position,
                        updatedAt = it.updatedAt,
                    )
                }
            )
        } catch (e: Exception) {
            Log.w("PlaybackPositionRepo", "Failed to refresh positions", e)
        }
    }

    /** Save a position locally first, then push to the server in the background. */
    suspend fun savePosition(livesetId: Long, positionSeconds: Long) {
        positionDao.upsert(
            PlaybackPositionEntity(
                livesetId = livesetId,
                positionSeconds = positionSeconds,
                updatedAt = java.time.Instant.now().toString(),
            )
        )
        if (!authRepository.isLoggedIn.value) return
        try {
            api.savePlaybackPosition(livesetId, SavePositionRequest(positionSeconds))
        } catch (e: Exception) {
            Log.w("PlaybackPositionRepo", "Failed to push position to server", e)
        }
    }

    suspend fun clearPosition(livesetId: Long) {
        positionDao.delete(livesetId)
        if (!authRepository.isLoggedIn.value) return
        try {
            api.clearPlaybackPosition(livesetId)
        } catch (e: Exception) {
            Log.w("PlaybackPositionRepo", "Failed to clear position on server", e)
        }
    }
}
