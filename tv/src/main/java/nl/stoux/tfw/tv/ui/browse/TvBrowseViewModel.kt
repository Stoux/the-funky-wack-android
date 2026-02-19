package nl.stoux.tfw.tv.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.database.dao.EditionWithContent
import nl.stoux.tfw.core.common.database.dao.LivesetWithDetails
import nl.stoux.tfw.core.common.database.entity.TrackEntity
import nl.stoux.tfw.core.common.repository.EditionRepository
import nl.stoux.tfw.service.playback.service.manager.LivesetTrackListener
import nl.stoux.tfw.service.playback.service.manager.LivesetTrackManager
import nl.stoux.tfw.service.playback.service.manager.UnbindCallback
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import nl.stoux.tfw.service.playback.service.queue.QueueState
import javax.inject.Inject

@HiltViewModel
class TvBrowseViewModel @Inject constructor(
    private val repository: EditionRepository,
    private val queueManager: QueueManager,
    private val livesetTrackManager: LivesetTrackManager,
) : ViewModel() {

    val editions: StateFlow<List<EditionWithContent>> = repository
        .getEditions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val queueState: StateFlow<QueueState> = queueManager.state

    // Current liveset and track from LivesetTrackManager
    private val _currentLiveset = MutableStateFlow<LivesetWithDetails?>(null)
    val currentLiveset: StateFlow<LivesetWithDetails?> = _currentLiveset.asStateFlow()

    private val _currentTrack = MutableStateFlow<TrackEntity?>(null)
    val currentTrack: StateFlow<TrackEntity?> = _currentTrack.asStateFlow()

    private var trackManagerUnbindCallback: UnbindCallback? = null

    init {
        viewModelScope.launch {
            repository.refreshEditions()
        }

        trackManagerUnbindCallback = livesetTrackManager.bind(object : LivesetTrackListener {
            override fun onLivesetChanged(liveset: LivesetWithDetails?) {
                _currentLiveset.value = liveset
            }

            override fun onTrackChanged(track: TrackEntity?) {
                _currentTrack.value = track
            }
        })
    }

    fun playLiveset(livesetId: Long) {
        queueManager.setContextFromLiveset(
            livesetId = livesetId,
            startPositionMs = 0L,
            autoplay = true
        )
    }

    fun getLivesetsForEdition(editionId: Long): StateFlow<List<LivesetWithDetails>> {
        return repository.getLivesets(editionId = editionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    override fun onCleared() {
        trackManagerUnbindCallback?.invoke()
        trackManagerUnbindCallback = null
        super.onCleared()
    }
}
