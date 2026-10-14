package nl.stoux.tfw.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.stoux.tfw.core.common.network.dto.AppNotificationDto
import nl.stoux.tfw.core.common.repository.NotificationsRepository
import nl.stoux.tfw.service.playback.service.queue.QueueManager
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationsRepository: NotificationsRepository,
    private val queueManager: QueueManager,
) : ViewModel() {

    val notifications: StateFlow<List<AppNotificationDto>> = notificationsRepository.notifications
    val unreadCount: StateFlow<Int> = notificationsRepository.unreadCount
    val isLoading: StateFlow<Boolean> = notificationsRepository.isLoading

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { notificationsRepository.refresh() }
    }

    fun markAllRead() {
        viewModelScope.launch { notificationsRepository.markAllRead() }
    }

    /** Mark read and start the liveset at the comment's timestamp. Returns false if there's nothing to play. */
    fun open(notification: AppNotificationDto): Boolean {
        viewModelScope.launch { notificationsRepository.markRead(notification.id) }
        val livesetId = notification.data.livesetId ?: return false
        val startMs = (notification.data.timestampSeconds ?: 0).toLong() * 1000L
        queueManager.setContextFromLiveset(livesetId, startPositionMs = startMs, autoplay = true)
        return true
    }
}
