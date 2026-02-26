package nl.stoux.tfw.tv.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * TV-specific settings (OLED protection, etc.).
 * Shared playback settings (audio quality, buffer) are in PlaybackSettingsRepository.
 */
class TvSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val oledAutoEnable = booleanPreferencesKey("oled::auto_enable")
        val oledTimeoutMinutes = intPreferencesKey("oled::timeout_minutes")
        val oledDriftEnabled = booleanPreferencesKey("oled::drift_enabled")
        val oledFadeEnabled = booleanPreferencesKey("oled::fade_enabled")
        val oledColorShiftEnabled = booleanPreferencesKey("oled::color_shift_enabled")
    }

    data class OledSettings(
        val autoEnable: Boolean = true,
        val timeoutMinutes: Int = 5,
        val driftEnabled: Boolean = true,
        val fadeEnabled: Boolean = true,
        val colorShiftEnabled: Boolean = true,
    )

    fun oledSettings(): Flow<OledSettings> = dataStore.data.map { prefs ->
        OledSettings(
            autoEnable = prefs[Keys.oledAutoEnable] ?: true,
            timeoutMinutes = prefs[Keys.oledTimeoutMinutes] ?: 5,
            driftEnabled = prefs[Keys.oledDriftEnabled] ?: true,
            fadeEnabled = prefs[Keys.oledFadeEnabled] ?: true,
            colorShiftEnabled = prefs[Keys.oledColorShiftEnabled] ?: true,
        )
    }

    suspend fun updateOledSettings(settings: OledSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.oledAutoEnable] = settings.autoEnable
            prefs[Keys.oledTimeoutMinutes] = settings.timeoutMinutes
            prefs[Keys.oledDriftEnabled] = settings.driftEnabled
            prefs[Keys.oledFadeEnabled] = settings.fadeEnabled
            prefs[Keys.oledColorShiftEnabled] = settings.colorShiftEnabled
        }
    }

    suspend fun setOledAutoEnable(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.oledAutoEnable] = enabled
        }
    }

    suspend fun setOledTimeout(minutes: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.oledTimeoutMinutes] = minutes
        }
    }

    suspend fun setOledDriftEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.oledDriftEnabled] = enabled
        }
    }

    suspend fun setOledFadeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.oledFadeEnabled] = enabled
        }
    }

    suspend fun setOledColorShiftEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.oledColorShiftEnabled] = enabled
        }
    }
}
