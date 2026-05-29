package nl.stoux.tfw.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.network.dto.auth.DeviceDto
import nl.stoux.tfw.core.common.repository.DeviceRepository
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val repository: DeviceRepository,
) : ViewModel() {

    val devices: StateFlow<List<DeviceDto>> = repository.devices

    init { refresh() }

    fun refresh() { viewModelScope.launch { repository.refresh() } }

    fun rename(id: Long, nickname: String) {
        viewModelScope.launch { repository.rename(id, nickname) }
    }

    fun setHidden(id: Long, hidden: Boolean) {
        viewModelScope.launch { repository.setHidden(id, hidden) }
    }

    fun remove(id: Long) {
        viewModelScope.launch { repository.remove(id) }
    }
}
