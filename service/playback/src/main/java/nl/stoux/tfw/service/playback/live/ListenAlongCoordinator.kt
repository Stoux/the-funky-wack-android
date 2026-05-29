package nl.stoux.tfw.service.playback.live

import android.util.Log
import com.pusher.client.channel.PresenceChannel
import com.pusher.client.channel.PresenceChannelEventListener
import com.pusher.client.channel.PusherEvent
import com.pusher.client.channel.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import nl.stoux.tfw.core.common.network.ApiService
import nl.stoux.tfw.core.common.network.dto.auth.JoinSessionRequest
import nl.stoux.tfw.core.common.network.dto.auth.LiveSessionDto
import nl.stoux.tfw.core.common.network.ws.ReverbClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the listen-along feature: triggers the HTTP join, subscribes to the
 * room's Reverb channel, parses host actions, and exposes them as flows for
 * the player to consume.
 *
 * Only one room can be active per process — joining a new one auto-leaves any
 * previous session.
 */
@Singleton
class ListenAlongCoordinator @Inject constructor(
    private val api: ApiService,
    private val reverbClient: ReverbClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _hostEvents = MutableSharedFlow<HostEvent>(extraBufferCapacity = 16)
    val hostEvents: SharedFlow<HostEvent> = _hostEvents.asSharedFlow()

    private val _listenerEvents = MutableSharedFlow<ListenerEvent>(extraBufferCapacity = 16)
    val listenerEvents: SharedFlow<ListenerEvent> = _listenerEvents.asSharedFlow()

    private val _activeRoom = MutableStateFlow<ActiveRoom?>(null)
    val activeRoom: StateFlow<ActiveRoom?> = _activeRoom.asStateFlow()

    /**
     * Last-known number of active listen-along sessions. Set by the Live lobby
     * after each refresh and read by the nav drawer to show a badge count.
     */
    private val _sessionCount = MutableStateFlow(0)
    val sessionCount: StateFlow<Int> = _sessionCount.asStateFlow()

    fun setSessionCount(count: Int) {
        _sessionCount.value = count
    }

    @Volatile private var presenceLiveSubscribers = 0
    private val presenceLiveLock = Any()

    data class ActiveRoom(
        val channelToken: String,
        val mode: String, // "synced" | "independent"
    )

    /**
     * Join a listen-along room. Calls the HTTP endpoint first, then subscribes
     * to the presence channel. For synced mode we wire host action handlers;
     * independent mode skips the channel (no host event mirroring needed).
     */
    suspend fun join(channelToken: String, mode: String): Result<Unit> = runCatching {
        leave() // tear down any prior room first

        val response = api.joinLiveSession(channelToken, JoinSessionRequest(mode))
        Log.d(TAG, "Joined room=$channelToken mode=${response.mode} member=${response.memberId}")

        if (response.mode == "synced") {
            reverbClient.connect()
            subscribeToRoom(channelToken)
        }

        _activeRoom.value = ActiveRoom(channelToken, response.mode)
    }

    /**
     * Leave the currently active room. Safe to call when no room is active.
     */
    suspend fun leave() {
        val current = _activeRoom.value ?: return
        runCatching { api.leaveLiveSession(current.channelToken) }
            .onFailure { Log.w(TAG, "leave HTTP failed", it) }
        reverbClient.unsubscribe(channelName(current.channelToken))
        _activeRoom.value = null
    }

    /**
     * Notify the server that the listener paused. The host's events get
     * buffered server-side until [resumeSync].
     */
    suspend fun pauseSync() {
        val current = _activeRoom.value ?: return
        if (current.mode != "synced") return
        runCatching { api.pauseSync(current.channelToken) }
            .onFailure { Log.w(TAG, "pause-sync HTTP failed", it) }
    }

    /**
     * Notify the server that the listener wants to resync. Caller should
     * also re-fetch room state to pick up any track change the host made
     * while the listener was paused.
     */
    suspend fun resumeSync() {
        val current = _activeRoom.value ?: return
        if (current.mode != "synced") return
        runCatching { api.resumeSync(current.channelToken) }
            .onFailure { Log.w(TAG, "resume-sync HTTP failed", it) }
    }

    /**
     * Fetch the current room state — used after [resumeSync] to find out
     * where the host is now (they may have switched tracks).
     */
    suspend fun fetchState(): LiveSessionDto? {
        val current = _activeRoom.value ?: return null
        return runCatching { api.getLiveRoomState(current.channelToken) }
            .onFailure { Log.w(TAG, "state HTTP failed", it) }
            .getOrNull()
    }

    /**
     * Switch from synced to independent mode. Server creates a new room
     * for the detached listener's ongoing playback. The listener stops
     * mirroring host actions but keeps playing locally.
     */
    suspend fun detach(): String? {
        val current = _activeRoom.value ?: return null
        if (current.mode != "synced") return null
        val response = runCatching { api.detachLiveSession(current.channelToken) }
            .onFailure { Log.w(TAG, "detach HTTP failed", it) }
            .getOrNull() ?: return null
        reverbClient.unsubscribe(channelName(current.channelToken))
        _activeRoom.value = ActiveRoom(current.channelToken, mode = "independent")
        return response.newRoomToken
    }

    private fun subscribeToRoom(channelToken: String) {
        val name = channelName(channelToken)
        val channel = reverbClient.subscribePresence(name, presenceListener)
        channel ?: run {
            Log.w(TAG, "Failed to subscribe to $name — Pusher not connected")
            return
        }
        // Pusher requires presence-channel bindings to be PresenceChannelEventListener
        // instances — a bare lambda would coerce to SubscriptionEventListener and crash.
        channel.bindPresence("host.seek") { event -> handleHostEvent(event, ::parseSeek) }
        channel.bindPresence("host.pause") { event -> handleHostEvent(event, ::parsePause) }
        channel.bindPresence("host.resume") { event -> handleHostEvent(event, ::parseResume) }
        channel.bindPresence("host.track-change") { event -> handleHostEvent(event, ::parseTrackChange) }
        channel.bindPresence("host.stop") { _ -> emitHost(HostEvent.Stop) }
        channel.bindPresence("listener.joined") { event -> handleListenerJoined(event) }
        channel.bindPresence("listener.left") { event -> handleListenerLeft(event) }
    }

    private fun PresenceChannel.bindPresence(eventName: String, onEvent: (PusherEvent?) -> Unit) {
        bind(eventName, object : PresenceChannelEventListener {
            override fun onEvent(event: PusherEvent?) = onEvent(event)
            override fun onUsersInformationReceived(channelName: String?, users: MutableSet<User>?) {}
            override fun userSubscribed(channelName: String?, user: User?) {}
            override fun userUnsubscribed(channelName: String?, user: User?) {}
            override fun onAuthenticationFailure(message: String?, e: Exception?) {}
            override fun onSubscriptionSucceeded(channelName: String?) {}
        })
    }

    private fun handleHostEvent(event: PusherEvent?, parse: (String) -> HostEvent?) {
        val data = event?.data ?: return
        runCatching { parse(data) }
            .onSuccess { it?.let { emitHost(it) } }
            .onFailure { Log.w(TAG, "Failed to parse host event ${event.eventName}: $data", it) }
    }

    private fun parseSeek(data: String): HostEvent? =
        readPosition(data)?.let { HostEvent.Seek(it) }

    private fun parsePause(data: String): HostEvent? =
        readPosition(data)?.let { HostEvent.Pause(it) }

    private fun parseResume(data: String): HostEvent? =
        readPosition(data)?.let { HostEvent.Resume(it) }

    private fun parseTrackChange(data: String): HostEvent? {
        val obj = json.parseToJsonElement(data).jsonObject
        val livesetId = obj["liveset_id"]?.jsonPrimitive?.longOrNull ?: return null
        val position = obj["position"]?.jsonPrimitive?.longOrNull ?: 0L
        val quality = obj["quality"]?.jsonPrimitive?.contentOrNullSafe()
        return HostEvent.TrackChange(livesetId, position, quality)
    }

    private fun readPosition(data: String): Long? =
        runCatching {
            json.parseToJsonElement(data).jsonObject["position"]?.jsonPrimitive?.longOrNull
        }.getOrNull()

    private fun handleListenerJoined(event: PusherEvent?) {
        val data = event?.data ?: return
        runCatching {
            val obj = json.parseToJsonElement(data).jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNullSafe() ?: "Listener"
            val count = obj["count"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0
            scope.launch { _listenerEvents.emit(ListenerEvent.Joined(name, count)) }
        }
    }

    private fun handleListenerLeft(event: PusherEvent?) {
        val data = event?.data ?: return
        runCatching {
            val obj = json.parseToJsonElement(data).jsonObject
            val count = obj["count"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0
            scope.launch { _listenerEvents.emit(ListenerEvent.Left(count)) }
        }
    }

    private fun emitHost(event: HostEvent) {
        scope.launch { _hostEvents.emit(event) }
    }

    private val presenceListener = object : PresenceChannelEventListener {
        override fun onUsersInformationReceived(channelName: String?, users: MutableSet<User>?) {
            Log.d(TAG, "Presence info on $channelName: ${users?.size ?: 0} users")
        }
        override fun userSubscribed(channelName: String?, user: User?) {
            Log.d(TAG, "User subscribed to $channelName: ${user?.id}")
        }
        override fun userUnsubscribed(channelName: String?, user: User?) {
            Log.d(TAG, "User unsubscribed from $channelName: ${user?.id}")
        }
        override fun onAuthenticationFailure(message: String?, e: Exception?) {
            Log.w(TAG, "Auth failure for presence channel: $message", e)
        }
        override fun onSubscriptionSucceeded(channelName: String?) {
            Log.d(TAG, "Subscribed: $channelName")
        }
        override fun onEvent(event: PusherEvent?) {
            Log.v(TAG, "Channel event: ${event?.eventName}")
        }
    }

    private fun channelName(channelToken: String): String = "presence-listen-along.$channelToken"

    private val _sessionsUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    /**
     * Emits whenever the global `presence-live` channel fires `.sessions.updated`,
     * signalling that the lobby list should be re-fetched.
     */
    val sessionsUpdated: SharedFlow<Unit> = _sessionsUpdated.asSharedFlow()

    /**
     * Subscribe to the global `presence-live` channel for lobby auto-refresh.
     * Call from the lobby screen on entry; pair with [unsubscribePresenceLive]
     * on exit.
     */
    fun subscribePresenceLive() {
        synchronized(presenceLiveLock) {
            presenceLiveSubscribers += 1
            if (presenceLiveSubscribers > 1) return // already bound, just bump refcount
        }
        reverbClient.connect()
        val name = "presence-live"
        val channel = reverbClient.subscribePresence(name, presenceListener) ?: run {
            Log.w(TAG, "Failed to subscribe to $name")
            return
        }
        channel.bindPresence("sessions.updated") { _ ->
            scope.launch { _sessionsUpdated.emit(Unit) }
        }
    }

    fun unsubscribePresenceLive() {
        synchronized(presenceLiveLock) {
            presenceLiveSubscribers = (presenceLiveSubscribers - 1).coerceAtLeast(0)
            if (presenceLiveSubscribers > 0) return // still active subscribers
        }
        reverbClient.unsubscribe("presence-live")
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        if (isString) content else null

    companion object {
        private const val TAG = "ListenAlong"
    }
}
