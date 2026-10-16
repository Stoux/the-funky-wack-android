package nl.stoux.tfw.core.common.repository

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.auth.PasskeyDto
import nl.stoux.tfw.core.common.network.dto.auth.PasskeyChallenge
import nl.stoux.tfw.core.common.network.dto.auth.RenamePasskeyRequest
import nl.stoux.tfw.core.common.network.dto.auth.StorePasskeyRequest
import nl.stoux.tfw.core.common.network.dto.auth.toChallenge
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The logged-in user's passkeys. Server-only like [DeviceRepository]; passkeys made on the
 * website show up here too (same relying party).
 */
@Singleton
class PasskeyRepository @Inject constructor(
    private val api: ApiService,
    private val authRepository: AuthRepository,
) {
    private val _passkeys = MutableStateFlow<List<PasskeyDto>>(emptyList())
    val passkeys: StateFlow<List<PasskeyDto>> = _passkeys.asStateFlow()

    suspend fun refresh() {
        runCatching { api.getPasskeys().passkeys }
            .onSuccess {
                _passkeys.value = it
                authRepository.setHasPasskeys(it.isNotEmpty())
            }
            .onFailure { Log.w(TAG, "Failed to refresh passkeys", it) }
    }

    /** Options for Credential Manager's create call; existing passkeys are excluded server-side. */
    suspend fun getRegisterChallenge(): PasskeyChallenge = api.getPasskeyRegisterOptions().toChallenge()

    suspend fun rename(id: Long, name: String): Result<PasskeyDto> = runCatching {
        api.renamePasskey(id, RenamePasskeyRequest(name)).passkey
    }.onSuccess {
        refresh()
    }.onFailure { Log.w(TAG, "Failed to rename passkey $id", it) }

    /** Store a new passkey from Credential Manager's registrationResponseJson. */
    suspend fun register(challengeId: String, name: String, registrationResponseJson: String): Result<PasskeyDto> = runCatching {
        val credential = Json.parseToJsonElement(registrationResponseJson).jsonObject
        api.registerPasskey(StorePasskeyRequest(challengeId, name, credential)).passkey
    }.onSuccess {
        refresh()
    }.onFailure { Log.w(TAG, "Failed to register passkey", it) }

    suspend fun remove(id: Long): Result<Unit> = runCatching {
        api.deletePasskey(id)
        Unit
    }.onSuccess {
        refresh()
    }.onFailure { Log.w(TAG, "Failed to remove passkey $id", it) }

    companion object { private const val TAG = "PasskeyRepository" }
}
