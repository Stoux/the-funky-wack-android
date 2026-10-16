package nl.stoux.tfw.core.common.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.stoux.tfw.core.common.device.DeviceIdManager
import nl.stoux.tfw.core.common.device.detectDeviceType
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.util.encryptedPrefs
import nl.stoux.tfw.core.common.network.dto.auth.DeviceCodeRequest
import nl.stoux.tfw.core.common.network.dto.auth.DeviceCodeResponse
import nl.stoux.tfw.core.common.network.dto.auth.DeviceCodePollResponse
import nl.stoux.tfw.core.common.network.dto.auth.DeviceRegisterRequest
import nl.stoux.tfw.core.common.network.dto.auth.LoginRequest
import nl.stoux.tfw.core.common.network.dto.auth.PasskeyLoginRequest
import nl.stoux.tfw.core.common.network.dto.auth.PasskeyChallenge
import nl.stoux.tfw.core.common.network.dto.auth.toChallenge
import nl.stoux.tfw.core.common.network.dto.auth.UserDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages authentication state: token storage, login/logout, device code flow.
 * Token is stored in EncryptedSharedPreferences (AES-256).
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ApiService,
    private val deviceIdManager: DeviceIdManager,
) {
    private val prefs: SharedPreferences by lazy { context.encryptedPrefs("tfw_auth_prefs") }

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<UserDto?>(null)
    val currentUser: StateFlow<UserDto?> = _currentUser.asStateFlow()

    /** Null until known (from /auth/user or a passkey sign in). */
    private val _hasPasskeys = MutableStateFlow<Boolean?>(null)
    val hasPasskeys: StateFlow<Boolean?> = _hasPasskeys.asStateFlow()

    /** The current bearer token, or null if not logged in. */
    val token: String?
        get() = prefs.getString(KEY_TOKEN, null)

    init {
        val hasToken = prefs.getString(KEY_TOKEN, null) != null
        _isLoggedIn.value = hasToken
        // Restore cached user name if available
        val userId = prefs.getLong(KEY_USER_ID, -1)
        val userName = prefs.getString(KEY_USER_NAME, null)
        val userEmail = prefs.getString(KEY_USER_EMAIL, null)
        if (hasToken && userId > 0 && userName != null && userEmail != null) {
            _currentUser.value = UserDto(userId, userName, userEmail)
        }
    }

    /**
     * Login with email and password. Returns the user on success.
     */
    suspend fun login(email: String, password: String, deviceName: String, deviceType: String? = null): UserDto {
        val response = api.login(LoginRequest(email, password, deviceName, deviceType))
        storeAuth(response.token, response.user)
        return response.user
    }

    /**
     * Logout: call server then clear local token.
     */
    suspend fun logout() {
        try {
            api.logout()
        } catch (e: Exception) {
            Log.w("AuthRepository", "Server logout failed (clearing locally anyway)", e)
        }
        clearAuth()
    }

    /**
     * Fetch the current user from the server (validates token).
     */
    suspend fun fetchUser(): UserDto {
        val response = api.getUser()
        storeUser(response.user)
        _hasPasskeys.value = response.hasPasskeys
        return response.user
    }

    /** Start a passkey sign in: the options for Credential Manager plus the challenge to answer. */
    suspend fun getPasskeyLoginChallenge(): PasskeyChallenge = api.getPasskeyLoginOptions().toChallenge()

    /**
     * Finish a passkey sign in with Credential Manager's authenticationResponseJson.
     * Same result as a password login: a token for this device.
     */
    suspend fun loginWithPasskey(
        challengeId: String,
        authenticationResponseJson: String,
        deviceName: String,
        deviceType: String? = null,
    ): UserDto {
        val credential = Json.parseToJsonElement(authenticationResponseJson).jsonObject
        val response = api.loginWithPasskey(PasskeyLoginRequest(challengeId, credential, deviceName, deviceType))
        storeAuth(response.token, response.user)
        _hasPasskeys.value = true
        return response.user
    }

    /** Set after adding/removing passkeys, so the "make a passkey" hint stays in step. */
    fun setHasPasskeys(hasPasskeys: Boolean) {
        _hasPasskeys.value = hasPasskeys
    }

    /**
     * Request a device code for TV/car login.
     */
    suspend fun requestDeviceCode(deviceName: String): DeviceCodeResponse {
        return api.requestDeviceCode(
            DeviceCodeRequest(deviceName, deviceIdManager.clientId)
        )
    }

    /**
     * Poll for device code authorization status.
     */
    suspend fun pollDeviceCode(code: String): DeviceCodePollResponse {
        val response = api.pollDeviceCode(code, deviceIdManager.clientId)
        if (response.status == "authorized" && response.token != null && response.user != null) {
            storeAuth(response.token, response.user)
            // Device-code authorize doesn't create a device row server-side, so
            // the first authenticated call from a TV/car would otherwise land
            // with UA-parsed name+type. Push the real fingerprint now.
            runCatching { registerDevice() }
                .onFailure { Log.w("AuthRepository", "registerDevice after device-code failed", it) }
        }
        return response
    }

    /** Push this device's true name + type to the server. Idempotent. */
    suspend fun registerDevice() {
        api.registerDevice(
            DeviceRegisterRequest(
                clientId = deviceIdManager.clientId,
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                deviceType = detectDeviceType(context),
            )
        )
    }

    /**
     * Authorize a device code (from the phone that is logged in).
     */
    suspend fun authorizeDeviceCode(code: String) {
        api.authorizeDeviceCode(code)
    }

    /**
     * Called when a 401 response is received — clears auth state.
     */
    fun onUnauthorized() {
        clearAuth()
    }

    private fun storeAuth(token: String, user: UserDto) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_USER_ID, user.id)
            .putString(KEY_USER_NAME, user.name)
            .putString(KEY_USER_EMAIL, user.email)
            .apply()
        _currentUser.value = user
        _isLoggedIn.value = true
    }

    private fun storeUser(user: UserDto) {
        prefs.edit()
            .putLong(KEY_USER_ID, user.id)
            .putString(KEY_USER_NAME, user.name)
            .putString(KEY_USER_EMAIL, user.email)
            .apply()
        _currentUser.value = user
    }

    private fun clearAuth() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .apply()
        _currentUser.value = null
        _hasPasskeys.value = null
        _isLoggedIn.value = false
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
    }
}
