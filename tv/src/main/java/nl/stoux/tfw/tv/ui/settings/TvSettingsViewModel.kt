package nl.stoux.tfw.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.repository.UserSettingsRepository
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository.AudioQuality
import nl.stoux.tfw.tv.data.TvSettingsRepository
import nl.stoux.tfw.tv.data.TvSettingsRepository.OledSettings
import javax.inject.Inject

@HiltViewModel
class TvSettingsViewModel @Inject constructor(
    private val tvSettingsRepository: TvSettingsRepository,
    private val playbackSettings: PlaybackSettingsRepository,
    private val authRepository: AuthRepository,
    private val userSettingsRepository: UserSettingsRepository,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn

    val listeningVisibility: StateFlow<String> = authRepository.currentUser
        .map { it?.listeningVisibility ?: "everyone" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "everyone")

    val userName: StateFlow<String?> = authRepository.currentUser
        .map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun signOut() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun setListeningVisibility(value: String) {
        viewModelScope.launch {
            userSettingsRepository.setListeningVisibility(value)
                .onSuccess { runCatching { authRepository.fetchUser() } }
        }
    }

    val oledSettings: StateFlow<OledSettings> = tvSettingsRepository.oledSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OledSettings()
        )

    val audioQuality: StateFlow<AudioQuality> = playbackSettings.audioQuality()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AudioQuality.DEFAULT
        )

    val bufferMinutes: StateFlow<Int> = playbackSettings.bufferDurationMinutes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlaybackSettingsRepository.DEFAULT_BUFFER_MINUTES
        )

    fun setOledAutoEnable(enabled: Boolean) {
        viewModelScope.launch {
            tvSettingsRepository.setOledAutoEnable(enabled)
        }
    }

    fun setOledTimeout(minutes: Int) {
        viewModelScope.launch {
            tvSettingsRepository.setOledTimeout(minutes)
        }
    }

    fun setOledDriftEnabled(enabled: Boolean) {
        viewModelScope.launch {
            tvSettingsRepository.setOledDriftEnabled(enabled)
        }
    }

    fun setOledFadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            tvSettingsRepository.setOledFadeEnabled(enabled)
        }
    }

    fun setOledColorShiftEnabled(enabled: Boolean) {
        viewModelScope.launch {
            tvSettingsRepository.setOledColorShiftEnabled(enabled)
        }
    }

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch {
            playbackSettings.setAudioQuality(quality)
        }
    }

    fun setBufferMinutes(minutes: Int) {
        viewModelScope.launch {
            playbackSettings.setBufferDurationMinutes(minutes)
        }
    }
}
