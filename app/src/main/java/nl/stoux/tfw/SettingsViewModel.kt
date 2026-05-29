package nl.stoux.tfw

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
import nl.stoux.tfw.service.playback.download.DownloadRepository
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository.AudioQuality
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val playbackSettings: PlaybackSettingsRepository,
    private val downloadRepository: DownloadRepository,
    private val authRepository: AuthRepository,
    private val userSettingsRepository: UserSettingsRepository,
) : ViewModel() {

    /** Current listening_visibility from cached user, or "everyone" when unknown. */
    val listeningVisibility: StateFlow<String> = authRepository.currentUser
        .map { it?.listeningVisibility ?: "everyone" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "everyone",
        )

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn

    fun setListeningVisibility(value: String) {
        viewModelScope.launch {
            userSettingsRepository.setListeningVisibility(value)
                .onSuccess { runCatching { authRepository.fetchUser() } }
        }
    }

    val bufferMinutes: StateFlow<Int> = playbackSettings.bufferDurationMinutes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlaybackSettingsRepository.DEFAULT_BUFFER_MINUTES
        )

    val audioQuality: StateFlow<AudioQuality> = playbackSettings.audioQuality()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AudioQuality.DEFAULT
        )

    val allowLossless: StateFlow<Boolean> = playbackSettings.allowLossless()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val offlineMode: StateFlow<Boolean> = playbackSettings.offlineMode()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val preferDownloadedQuality: StateFlow<Boolean> = playbackSettings.preferDownloadedQuality()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val totalDownloadedBytes: StateFlow<Long> = downloadRepository.totalDownloadedBytes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    fun setBufferMinutes(minutes: Int) {
        viewModelScope.launch {
            playbackSettings.setBufferDurationMinutes(minutes)
        }
    }

    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch {
            playbackSettings.setAudioQuality(quality)
        }
    }

    fun setAllowLossless(allow: Boolean) {
        viewModelScope.launch {
            playbackSettings.setAllowLossless(allow)
        }
    }

    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            playbackSettings.setOfflineMode(enabled)
        }
    }

    fun setPreferDownloadedQuality(prefer: Boolean) {
        viewModelScope.launch {
            playbackSettings.setPreferDownloadedQuality(prefer)
        }
    }
}
