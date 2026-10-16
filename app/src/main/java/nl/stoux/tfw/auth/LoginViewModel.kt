package nl.stoux.tfw.auth

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.device.detectDeviceType
import nl.stoux.tfw.core.common.network.dto.auth.UserDto
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
    // Device code authorization
    val deviceCode: String = "",
    val isAuthorizingDevice: Boolean = false,
    val deviceAuthError: String? = null,
    val deviceAuthSuccess: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    val isLoggedIn = authRepository.isLoggedIn
    val currentUser = authRepository.currentUser

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email, error = null)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun login() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Email and password are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                authRepository.login(state.email, state.password, deviceName, detectDeviceType(context))
                _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
                // Learn whether to suggest making a passkey
                runCatching { authRepository.fetchUser() }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Login failed"
                )
            }
        }
    }

    /** Sign in with a passkey (usernameless): options → Credential Manager → token. */
    fun loginWithPasskey(credentials: PasskeyCredentials) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val challenge = authRepository.getPasskeyLoginChallenge()
                val response = credentials.signIn(challenge.requestJson)
                if (response == null) {
                    // Cancelled by the user
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                authRepository.loginWithPasskey(challenge.challengeId, response, deviceName, detectDeviceType(context))
                _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = PasskeyCredentials.messageFor(e))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = LoginUiState()
        }
    }

    // Device code authorization (from phone to authorize TV/car)
    fun updateDeviceCode(code: String) {
        _uiState.value = _uiState.value.copy(deviceCode = code, deviceAuthError = null)
    }

    fun authorizeDevice() {
        val code = _uiState.value.deviceCode.trim()
        if (code.isBlank()) {
            _uiState.value = _uiState.value.copy(deviceAuthError = "Enter a device code")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthorizingDevice = true, deviceAuthError = null)
            try {
                authRepository.authorizeDeviceCode(code)
                _uiState.value = _uiState.value.copy(
                    isAuthorizingDevice = false,
                    deviceAuthSuccess = true,
                    deviceCode = "",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAuthorizingDevice = false,
                    deviceAuthError = e.message ?: "Authorization failed"
                )
            }
        }
    }

    fun clearDeviceAuthSuccess() {
        _uiState.value = _uiState.value.copy(deviceAuthSuccess = false)
    }
}
