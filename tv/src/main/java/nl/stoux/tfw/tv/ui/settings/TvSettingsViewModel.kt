package nl.stoux.tfw.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository
import nl.stoux.tfw.service.playback.settings.PlaybackSettingsRepository.AudioQuality
import nl.stoux.tfw.tv.data.TvSettingsRepository
import nl.stoux.tfw.tv.data.TvSettingsRepository.OledSettings
import javax.inject.Inject

@HiltViewModel
class TvSettingsViewModel @Inject constructor(
    private val tvSettingsRepository: TvSettingsRepository,
    private val playbackSettings: PlaybackSettingsRepository,
) : ViewModel() {

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
