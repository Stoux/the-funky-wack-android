package nl.stoux.tfw.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.database.dao.EditionWithContent
import nl.stoux.tfw.core.common.repository.EditionRepository

@HiltViewModel
class EditionListViewModel @Inject constructor(
    private val repository: EditionRepository,
) : ViewModel() {

    val editions: StateFlow<List<EditionWithContent>> = repository
        .getEditions()
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Kick off a refresh on startup (stale-while-revalidate)
        viewModelScope.launch {
            repository.refreshEditions()
        }
    }
}
