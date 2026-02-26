package nl.stoux.tfw

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository.AudioQuality
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val playbackSettings: PlaybackSettingsRepository,
) : ViewModel() {

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
}
