package nl.stoux.tfw.tv.ui.auth

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.auth.AuthRepository
import retrofit2.HttpException
import javax.inject.Inject

data class DeviceCodeUiState(
    val code: String? = null,
    val expiresAt: String? = null,
    val isLoading: Boolean = false,
    val isAuthorized: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class TvDeviceCodeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCodeUiState())
    val uiState: StateFlow<DeviceCodeUiState> = _uiState.asStateFlow()

    val isLoggedIn = authRepository.isLoggedIn

    private var pollJob: Job? = null

    fun requestCode() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL} (TV)"
                val response = authRepository.requestDeviceCode(deviceName)
                _uiState.value = _uiState.value.copy(
                    code = response.code,
                    expiresAt = response.expiresAt,
                    isLoading = false,
                )
                startPolling(response.code)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to generate code",
                )
            }
        }
    }

    private fun startPolling(code: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var consecutiveErrors = 0
            // Poll every 5 seconds for up to 10 minutes
            repeat(120) {
                delay(5000)
                try {
                    val response = authRepository.pollDeviceCode(code)
                    consecutiveErrors = 0
                    if (response.status == "authorized") {
                        _uiState.value = _uiState.value.copy(isAuthorized = true)
                        return@launch
                    }
                } catch (e: HttpException) {
                    // 410 Gone is the backend's explicit "expired" signal; everything
                    // else (422 validation, 429 throttle, 5xx) is transient.
                    if (e.code() == 410) {
                        _uiState.value = _uiState.value.copy(error = "Code expired or invalid.", code = null)
                        return@launch
                    }
                    consecutiveErrors++
                } catch (_: Exception) {
                    consecutiveErrors++
                }
                if (consecutiveErrors >= 3) {
                    _uiState.value = _uiState.value.copy(error = "Connection lost. Please try again.", code = null)
                    return@launch
                }
            }
            _uiState.value = _uiState.value.copy(
                error = "Code expired. Please try again.",
                code = null,
            )
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
