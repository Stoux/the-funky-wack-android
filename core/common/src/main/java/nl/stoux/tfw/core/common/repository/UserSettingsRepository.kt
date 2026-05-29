package nl.stoux.tfw.core.common.repository

import android.util.Log
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.auth.VisibilitySettingRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps user-level settings that live server-side (visibility, profile,
 * password). Auth-state changes are owned by AuthRepository; this is for
 * everything else that needs to round-trip to the API.
 */
@Singleton
class UserSettingsRepository @Inject constructor(
    private val api: ApiService,
) {
    /**
     * Update listening visibility. Valid values: "everyone", "authenticated", "nobody".
     */
    suspend fun setListeningVisibility(value: String): Result<Unit> = runCatching {
        api.updateVisibility(VisibilitySettingRequest(value))
    }.onFailure { Log.w("UserSettings", "Failed to update visibility to $value", it) }
}
