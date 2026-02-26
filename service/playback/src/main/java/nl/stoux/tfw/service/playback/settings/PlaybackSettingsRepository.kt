package nl.stoux.tfw.service.playback.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared playback settings used across all platforms (phone, TV, automotive).
 * Platform-specific settings (like TV OLED protection) stay in their own modules.
 */
@Singleton
class PlaybackSettingsRepository @Inject constructor(
    @PlaybackSettingsDataStore private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val bufferDurationMinutes = intPreferencesKey("playback::buffer_duration_minutes")
        val audioQuality = stringPreferencesKey("playback::audio_quality")
        val allowLossless = booleanPreferencesKey("playback::allow_lossless")
        val offlineMode = booleanPreferencesKey("playback::offline_mode")
        val preferDownloadedQuality = booleanPreferencesKey("playback::prefer_downloaded_quality")
    }

    companion object {
        const val DEFAULT_BUFFER_MINUTES = 5
        const val MIN_BUFFER_MINUTES = 1
        const val MAX_BUFFER_MINUTES = 999
    }

    // Audio quality options
    enum class AudioQuality(val key: String, val label: String) {
        LOW("low", "Low"),
        HIGH("high", "High"),
        LOSSLESS("lossless", "Lossless");

        companion object {
            val DEFAULT = HIGH
            fun fromKey(key: String?): AudioQuality =
                entries.find { it.key == key } ?: DEFAULT
        }
    }

    /**
     * Buffer duration in minutes (1-999). Default is 5 minutes.
     */
    fun bufferDurationMinutes(): Flow<Int> = dataStore.data.map { prefs ->
        (prefs[Keys.bufferDurationMinutes] ?: DEFAULT_BUFFER_MINUTES)
            .coerceIn(MIN_BUFFER_MINUTES, MAX_BUFFER_MINUTES)
    }

    suspend fun setBufferDurationMinutes(minutes: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.bufferDurationMinutes] = minutes.coerceIn(MIN_BUFFER_MINUTES, MAX_BUFFER_MINUTES)
        }
    }

    fun audioQuality(): Flow<AudioQuality> = dataStore.data.map { prefs ->
        AudioQuality.fromKey(prefs[Keys.audioQuality])
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        dataStore.edit { prefs ->
            prefs[Keys.audioQuality] = quality.key
        }
    }

    /**
     * Whether lossless (WAV) playback is allowed.
     * Default is false - WAV files can be ~1GB per set.
     */
    fun allowLossless(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.allowLossless] ?: false
    }

    suspend fun setAllowLossless(allow: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.allowLossless] = allow
        }
    }

    /**
     * Whether to only show and play downloaded content.
     * When enabled, livesets without downloads are hidden from the browse list,
     * and playback will fail for non-downloaded content.
     */
    fun offlineMode(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.offlineMode] ?: false
    }

    suspend fun setOfflineMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.offlineMode] = enabled
        }
    }

    /**
     * Whether to prefer the downloaded quality over streaming quality.
     * When enabled, if a liveset is downloaded (even at lower quality),
     * use the local file instead of streaming higher quality.
     */
    fun preferDownloadedQuality(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.preferDownloadedQuality] ?: true // Default to true - prefer local files
    }

    suspend fun setPreferDownloadedQuality(prefer: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.preferDownloadedQuality] = prefer
        }
    }
}
