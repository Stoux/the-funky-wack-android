package nl.stoux.tfw.invites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.network.dto.auth.InviteDto
import nl.stoux.tfw.core.common.repository.InviteRepository
import javax.inject.Inject

@HiltViewModel
class InvitesViewModel @Inject constructor(
    private val repository: InviteRepository,
) : ViewModel() {

    val invites: StateFlow<List<InviteDto>> = repository.invites

    init { refresh() }

    fun refresh() { viewModelScope.launch { repository.refresh() } }

    fun createInvite(onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            repository.create()?.let(onCreated)
        }
    }
}
