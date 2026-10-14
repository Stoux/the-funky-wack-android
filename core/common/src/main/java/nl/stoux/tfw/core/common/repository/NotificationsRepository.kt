package nl.stoux.tfw.core.common.repository

import android.util.Log
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.AppNotificationDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app notifications (comment replies / new comments). Network-backed, no
 * push: refreshed on login, when the drawer opens and when the screen opens.
 * State is cleared on logout.
 */
@Singleton
class NotificationsRepository @Inject constructor(
    private val api: ApiService,
    private val authRepository: AuthRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    private val _notifications = MutableStateFlow<List<AppNotificationDto>>(emptyList())
    val notifications: StateFlow<List<AppNotificationDto>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        scope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                if (loggedIn) refresh() else clear()
            }
        }
    }

    suspend fun refresh(): Result<Unit> {
        if (!authRepository.isLoggedIn.value) return Result.success(Unit)
        return refreshMutex.withLock {
            _isLoading.value = true
            try {
                runCatching { api.getNotifications() }
                    .onSuccess {
                        _notifications.value = it.notifications
                        _unreadCount.value = it.unreadCount
                    }
                    .onFailure { Log.w(TAG, "Failed to refresh notifications", it) }
                    .map { }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Fire-and-forget refresh, e.g. when the nav drawer opens. */
    fun refreshAsync() {
        scope.launch { refresh() }
    }

    /** Optimistically mark one notification read; reverts on failure. */
    suspend fun markRead(id: String): Result<Unit> {
        val before = _notifications.value
        val target = before.firstOrNull { it.id == id } ?: return Result.success(Unit)
        if (target.isRead) return Result.success(Unit)

        val now = OffsetDateTime.now().toString()
        _notifications.value = before.map { if (it.id == id) it.copy(readAt = now) else it }
        _unreadCount.value = (_unreadCount.value - 1).coerceAtLeast(0)

        return runCatching { api.markNotificationRead(id); Unit }
            .onFailure {
                Log.w(TAG, "Failed to mark notification $id read", it)
                _notifications.value = before
                _unreadCount.value = _unreadCount.value + 1
            }
    }

    /** Optimistically mark everything read; refreshes from server on failure. */
    suspend fun markAllRead(): Result<Unit> {
        val now = OffsetDateTime.now().toString()
        _notifications.value = _notifications.value.map { if (it.isRead) it else it.copy(readAt = now) }
        _unreadCount.value = 0

        return runCatching { api.markAllNotificationsRead(); Unit }
            .onFailure {
                Log.w(TAG, "Failed to mark all notifications read", it)
                refresh()
            }
    }

    private fun clear() {
        _notifications.value = emptyList()
        _unreadCount.value = 0
    }

    companion object { private const val TAG = "NotificationsRepository" }
}
