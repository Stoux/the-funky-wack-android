package nl.stoux.tfw.service.playback.service.resume

import kotlinx.coroutines.flow.Flow

/**
 * Repository for persisting and retrieving last playback state.
 */
interface PlaybackStateRepository {
    data class State(
        val mediaId: String,
        val positionMs: Long,
    )

    fun lastState(): Flow<State?>

    suspend fun saveState(mediaId: String, positionMs: Long)
}