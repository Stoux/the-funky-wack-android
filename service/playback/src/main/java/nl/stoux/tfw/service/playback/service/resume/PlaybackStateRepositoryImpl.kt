package nl.stoux.tfw.service.playback.service.resume

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackStateRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PlaybackStateRepository {

    private object Keys {
        val mediaId = stringPreferencesKey("playback::last::media_id")
        val position = longPreferencesKey("playback::last::position_ms")
    }

    override fun lastState(): Flow<PlaybackStateRepository.State?> =
        dataStore.data.map { prefs ->
            val id = prefs[Keys.mediaId]
            val pos = prefs[Keys.position] ?: 0L
            if (id.isNullOrEmpty()) null else PlaybackStateRepository.State(id, pos)
        }

    override suspend fun saveState(state: PlaybackStateRepository.State) {
        dataStore.edit { prefs ->
            prefs[Keys.mediaId] = state.mediaId
            prefs[Keys.position] = state.positionMs
        }
    }
}