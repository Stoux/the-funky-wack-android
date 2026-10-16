package nl.stoux.tfw.auth

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.network.dto.auth.PasskeyDto
import nl.stoux.tfw.core.common.repository.PasskeyRepository
import javax.inject.Inject

/** Passkeys section on the profile: list, add (Credential Manager create), remove. */
@HiltViewModel
class PasskeysViewModel @Inject constructor(
    private val passkeyRepository: PasskeyRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val passkeys: StateFlow<List<PasskeyDto>> = passkeyRepository.passkeys
    val hasPasskeys: StateFlow<Boolean?> = authRepository.hasPasskeys

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun refresh() {
        viewModelScope.launch { passkeyRepository.refresh() }
    }

    fun add(credentials: PasskeyCredentials) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val challenge = passkeyRepository.getRegisterChallenge()
                val response = credentials.create(challenge.requestJson) ?: return@launch
                passkeyRepository.register(challenge.challengeId, "${Build.MANUFACTURER} ${Build.MODEL}", response)
                    .onSuccess { _messages.tryEmit("Passkey added") }
                    .onFailure { _messages.tryEmit(PasskeyCredentials.messageFor(it)) }
            } catch (e: Exception) {
                _messages.tryEmit(PasskeyCredentials.messageFor(e))
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun rename(passkey: PasskeyDto, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == passkey.name) return
        viewModelScope.launch {
            passkeyRepository.rename(passkey.id, trimmed.take(255))
                .onFailure { _messages.tryEmit(PasskeyCredentials.messageFor(it)) }
        }
    }

    fun remove(passkey: PasskeyDto) {
        viewModelScope.launch {
            passkeyRepository.remove(passkey.id)
                .onSuccess { _messages.tryEmit("Passkey removed") }
                .onFailure { _messages.tryEmit(PasskeyCredentials.messageFor(it)) }
        }
    }
}
