package nl.stoux.tfw.automotive

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
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

/**
 * Single-screen login for Automotive. Driver distraction guidelines mean no
 * keyboard input — users pair the car by visiting the TFW site on another
 * device and entering the displayed code.
 */
@AndroidEntryPoint
class AutomotiveLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AutomotiveLoginScreen(onAuthorized = { finish() })
                }
            }
        }
    }
}

@HiltViewModel
class AutomotiveLoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    data class UiState(
        val code: String? = null,
        val isLoading: Boolean = true,
        val isAuthorized: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn

    private var pollJob: Job? = null

    init { requestCode() }

    fun requestCode() {
        viewModelScope.launch {
            _state.value = UiState(isLoading = true)
            runCatching {
                authRepository.requestDeviceCode("Automotive — ${Build.MANUFACTURER} ${Build.MODEL}")
            }.onSuccess { response ->
                _state.value = UiState(code = response.code, isLoading = false)
                startPolling(response.code)
            }.onFailure {
                _state.value = UiState(isLoading = false, error = it.message ?: "Failed to request code")
            }
        }
    }

    private fun startPolling(code: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var consecutiveErrors = 0
            repeat(120) {
                delay(5_000)
                try {
                    val poll = authRepository.pollDeviceCode(code)
                    consecutiveErrors = 0
                    if (poll.status == "authorized") {
                        _state.value = _state.value.copy(isAuthorized = true)
                        return@launch
                    }
                } catch (e: HttpException) {
                    // 410 Gone is the backend's explicit "expired" signal; everything
                    // else (422 validation, 429 throttle, 5xx) is transient.
                    if (e.code() == 410) {
                        _state.value = _state.value.copy(error = "Code expired. Tap retry.", code = null)
                        return@launch
                    }
                    consecutiveErrors++
                } catch (_: Exception) {
                    consecutiveErrors++
                }
                if (consecutiveErrors >= 3) {
                    _state.value = _state.value.copy(error = "Connection lost.", code = null)
                    return@launch
                }
            }
            _state.value = _state.value.copy(error = "Code expired.", code = null)
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}

@Composable
private fun AutomotiveLoginScreen(
    onAuthorized: () -> Unit,
    viewModel: AutomotiveLoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    LaunchedEffect(isLoggedIn) { if (isLoggedIn) onAuthorized() }

    Box(modifier = Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        when {
            state.isLoading -> CircularProgressIndicator()
            state.error != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(state.error!!, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { viewModel.requestCode() }) { Text("Retry") }
            }
            state.code != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Pair this car", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Visit tfw.stoux.nl on another device, sign in, and enter:",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    state.code!!,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
