package nl.stoux.tfw.core.common.repository

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.auth.InviteDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InviteRepository @Inject constructor(
    private val api: ApiService,
) {
    private val _invites = MutableStateFlow<List<InviteDto>>(emptyList())
    val invites: StateFlow<List<InviteDto>> = _invites.asStateFlow()

    suspend fun refresh() {
        runCatching { api.getInvites().invites }
            .onSuccess { _invites.value = it }
            .onFailure { Log.w(TAG, "Failed to refresh invites", it) }
    }

    /** Generate a new invite code; returns the created code or null on failure. */
    suspend fun create(): String? {
        return runCatching { api.createInvite().invite }
            .onSuccess { invite -> _invites.value = _invites.value + invite }
            .onFailure { Log.w(TAG, "Failed to create invite", it) }
            .getOrNull()?.code
    }

    companion object { private const val TAG = "InviteRepository" }
}
