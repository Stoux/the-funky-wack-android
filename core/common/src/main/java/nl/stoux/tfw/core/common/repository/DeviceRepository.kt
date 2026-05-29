package nl.stoux.tfw.core.common.repository

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.auth.DeviceDto
import nl.stoux.tfw.core.common.network.dto.auth.DeviceUpdateRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Server-only device list for the device management screen. No Room cache —
 * the list changes infrequently and is small.
 */
@Singleton
class DeviceRepository @Inject constructor(
    private val api: ApiService,
) {
    private val _devices = MutableStateFlow<List<DeviceDto>>(emptyList())
    val devices: StateFlow<List<DeviceDto>> = _devices.asStateFlow()

    suspend fun refresh() {
        runCatching { api.getDevices().devices }
            .onSuccess { _devices.value = it }
            .onFailure { Log.w(TAG, "Failed to refresh devices", it) }
    }

    suspend fun rename(id: Long, nickname: String) {
        runCatching { api.updateDevice(id, DeviceUpdateRequest(nickname)) }
            .onFailure { Log.w(TAG, "Failed to rename device $id", it) }
        refresh()
    }

    suspend fun setHidden(id: Long, hidden: Boolean) {
        runCatching { if (hidden) api.hideDevice(id) else api.showDevice(id) }
            .onFailure { Log.w(TAG, "Failed to toggle device visibility $id", it) }
        refresh()
    }

    suspend fun remove(id: Long) {
        runCatching { api.deleteDevice(id) }
            .onFailure { Log.w(TAG, "Failed to remove device $id", it) }
        refresh()
    }

    companion object { private const val TAG = "DeviceRepository" }
}
