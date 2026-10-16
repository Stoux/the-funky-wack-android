package nl.stoux.tfw.core.common.repository

import android.util.Log
import com.pusher.client.channel.PrivateChannelEventListener
import com.pusher.client.channel.PusherEvent
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.AppNotificationDataDto
import nl.stoux.tfw.core.common.network.dto.AppNotificationDto
import nl.stoux.tfw.core.common.network.ws.ReverbClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app notifications (comment replies / new comments). Refreshed on login, when
 * the drawer opens and when the screen opens; while the app is in the foreground
 * new ones also arrive live over the private Reverb channel `user.{id}`.
 * State is cleared on logout.
 */
@Singleton
class NotificationsRepository @Inject constructor(
    private val api: ApiService,
    private val authRepository: AuthRepository,
    private val reverbClient: ReverbClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val liveRequested = MutableStateFlow(false)
    private var liveChannel: String? = null

    private val _notifications = MutableStateFlow<List<AppNotificationDto>>(emptyList())
    val notifications: StateFlow<List<AppNotificationDto>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Cursor for the next (older) page, null when everything is loaded. */
    @Volatile private var nextCursor: String? = null
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    init {
        scope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                if (loggedIn) refresh() else clear()
            }
        }

        // Follow login/logout while live: (re)subscribe to the current user's channel or drop it
        scope.launch {
            combine(authRepository.currentUser.map { it?.id }, liveRequested) { userId, live -> userId.takeIf { live } }
                .distinctUntilChanged()
                .collect { userId -> switchLiveChannel(userId) }
        }
    }

    /** Receive new notifications live while the app is in the foreground. Pair with [stopLive]. */
    fun startLive() {
        liveRequested.value = true
    }

    fun stopLive() {
        liveRequested.value = false
    }

    private fun switchLiveChannel(userId: Long?) {
        liveChannel?.let { name ->
            reverbClient.unsubscribe(name)
            reverbClient.release()
            liveChannel = null
        }
        if (userId == null) return

        reverbClient.acquire()
        val name = "private-user.$userId"
        if (reverbClient.subscribePrivate(name, liveListener, BROADCAST_NOTIFICATION_EVENT) == null) {
            Log.w(TAG, "Failed to subscribe to $name")
            reverbClient.release()
            return
        }
        liveChannel = name
    }

    /** Payload is the notification's `data` plus its `id` (and `type`, same as data.type). */
    private fun onLiveNotification(payload: String) {
        val notification = runCatching {
            val obj = json.parseToJsonElement(payload).jsonObject
            AppNotificationDto(
                id = obj["id"]?.jsonPrimitive?.content ?: return,
                type = obj["type"]?.jsonPrimitive?.contentOrNull,
                data = json.decodeFromJsonElement(AppNotificationDataDto.serializer(), obj),
                createdAt = OffsetDateTime.now().toString(),
            )
        }.onFailure { Log.w(TAG, "Failed to parse live notification: $payload", it) }.getOrNull() ?: return

        // A refresh may already have picked it up
        if (_notifications.value.any { it.id == notification.id }) return
        _notifications.update { listOf(notification) + it }
        _unreadCount.update { it + 1 }
    }

    private val liveListener = object : PrivateChannelEventListener {
        override fun onEvent(event: PusherEvent?) {
            event?.data?.let { onLiveNotification(it) }
        }
        override fun onSubscriptionSucceeded(channelName: String?) {
            Log.d(TAG, "Subscribed: $channelName")
        }
        override fun onAuthenticationFailure(message: String?, e: Exception?) {
            Log.w(TAG, "Auth failure for user channel: $message", e)
        }
    }

    suspend fun refresh(): Result<Unit> {
        if (!authRepository.isLoggedIn.value) return Result.success(Unit)
        return refreshMutex.withLock {
            _isLoading.value = true
            try {
                runCatching { api.getNotifications() }
                    .onSuccess {
                        // Back to the first page; older pages load again on scroll
                        _notifications.value = it.notifications
                        _unreadCount.value = it.unreadCount
                        nextCursor = it.nextCursor
                        _hasMore.value = it.nextCursor != null
                    }
                    .onFailure { Log.w(TAG, "Failed to refresh notifications", it) }
                    .map { }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Append the next (older) page. No-op when everything is loaded or a load is running. */
    suspend fun loadMore(): Result<Unit> {
        val cursor = nextCursor ?: return Result.success(Unit)
        if (!refreshMutex.tryLock()) return Result.success(Unit)
        return try {
            _isLoading.value = true
            runCatching { api.getNotifications(cursor = cursor) }
                .onSuccess { page ->
                    // Order is stable while new ones arrive live, but skip any we already have
                    val known = _notifications.value.mapTo(HashSet()) { it.id }
                    _notifications.update { it + page.notifications.filter { n -> n.id !in known } }
                    _unreadCount.value = page.unreadCount
                    nextCursor = page.nextCursor
                    _hasMore.value = page.nextCursor != null
                }
                .onFailure { Log.w(TAG, "Failed to load more notifications", it) }
                .map { }
        } finally {
            _isLoading.value = false
            refreshMutex.unlock()
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
        nextCursor = null
        _hasMore.value = false
    }

    companion object {
        private const val TAG = "NotificationsRepository"

        /** Laravel's event name for broadcast notifications (Echo's `.notification()`). */
        private const val BROADCAST_NOTIFICATION_EVENT = "Illuminate\\Notifications\\Events\\BroadcastNotificationCreated"
    }
}
