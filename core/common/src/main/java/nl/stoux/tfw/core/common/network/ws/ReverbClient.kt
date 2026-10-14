package nl.stoux.tfw.core.common.network.ws

import android.util.Log
import com.pusher.client.AuthorizationFailureException
import com.pusher.client.Authorizer
import com.pusher.client.Pusher
import com.pusher.client.PusherOptions
import com.pusher.client.channel.Channel
import com.pusher.client.channel.ChannelEventListener
import com.pusher.client.channel.PresenceChannel
import com.pusher.client.channel.PresenceChannelEventListener
import com.pusher.client.channel.PrivateChannel
import com.pusher.client.channel.PrivateChannelEventListener
import com.pusher.client.channel.SubscriptionEventListener
import com.pusher.client.connection.ConnectionEventListener
import com.pusher.client.connection.ConnectionStateChange
import dagger.Lazy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.stoux.tfw.core.common.auth.AuthRepository
import nl.stoux.tfw.core.common.device.DeviceIdManager
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connection state exposed to the rest of the app. Subscribers shouldn't care
 * about the Pusher SDK details — just whether we're connected.
 */
enum class ReverbConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, FAILED }

/**
 * Thin wrapper around the Pusher Java client. Talks to Laravel Reverb at the
 * host defined by [ReverbConfig]. Auth goes through /api/broadcast/auth with
 * the user's Sanctum bearer and X-Client-ID so the backend can attribute
 * presence and disconnect events to the right device.
 *
 * Subscribers only ever see [Channel] / [PresenceChannel] handles, never the
 * underlying [Pusher] instance — connect/disconnect lifecycle is owned here.
 */
@Singleton
class ReverbClient @Inject constructor(
    private val authRepository: AuthRepository,
    private val deviceIdManager: DeviceIdManager,
    private val okHttpClient: Lazy<OkHttpClient>,
) {
    private val _state = MutableStateFlow(ReverbConnectionState.DISCONNECTED)
    val state: StateFlow<ReverbConnectionState> = _state.asStateFlow()

    @Volatile private var pusher: Pusher? = null

    /**
     * Ref-counted consumers — each [acquire] must be matched by exactly one
     * [release]. The underlying Pusher connection opens on the first acquire
     * and closes when the last consumer releases. Guards against one consumer
     * tearing down the socket out from under another (e.g. the service
     * stopping while the lobby's presence-live is still subscribed).
     */
    private var refCount = 0

    /**
     * Acquire a hold on the connection. Opens the socket on the first hold,
     * no-ops on subsequent acquires. Pair with [release].
     */
    @Synchronized
    fun acquire() {
        refCount += 1
        if (refCount > 1) return
        openConnection()
    }

    /**
     * Release a previously-acquired hold. Tears down the socket when the
     * last consumer releases. Calling without a matching [acquire] is a no-op.
     */
    @Synchronized
    fun release() {
        if (refCount == 0) return
        refCount -= 1
        if (refCount > 0) return
        closeConnection()
    }

    /**
     * Force the socket down regardless of refcount. Only call from the service
     * teardown — anything still holding a ref is about to die with the process.
     */
    @Synchronized
    fun forceDisconnect() {
        refCount = 0
        closeConnection()
    }

    private fun openConnection() {
        if (pusher != null) return
        if (!ReverbConfig.isConfigured) {
            Log.w("ReverbClient", "Reverb not configured (missing REVERB_APP_KEY or REVERB_HOST in secrets). Skipping connect.")
            return
        }
        val options = PusherOptions().apply {
            setHost(ReverbConfig.HOST)
            setWssPort(ReverbConfig.PORT)
            setWsPort(ReverbConfig.PORT)
            setUseTLS(ReverbConfig.USE_TLS)
            setActivityTimeout(ReverbConfig.ACTIVITY_TIMEOUT_MS)
            setAuthorizer(broadcastAuthorizer)
        }
        pusher = Pusher(ReverbConfig.APP_KEY, options).also {
            _state.value = ReverbConnectionState.CONNECTING
            it.connect(connectionListener)
        }
    }

    private fun closeConnection() {
        pusher?.disconnect()
        pusher = null
        _state.value = ReverbConnectionState.DISCONNECTED
    }

    fun subscribe(channelName: String, listener: ChannelEventListener): Channel? =
        pusher?.subscribe(channelName, listener)

    fun subscribePresence(channelName: String, listener: PresenceChannelEventListener): PresenceChannel? =
        pusher?.subscribePresence(channelName, listener)

    fun subscribePrivate(channelName: String, listener: PrivateChannelEventListener, vararg eventNames: String): PrivateChannel? =
        pusher?.subscribePrivate(channelName, listener, *eventNames)

    fun bindEvent(channelName: String, eventName: String, listener: SubscriptionEventListener) {
        pusher?.getChannel(channelName)?.bind(eventName, listener)
            ?: pusher?.getPresenceChannel(channelName)?.bind(eventName, listener)
    }

    fun unsubscribe(channelName: String) {
        pusher?.unsubscribe(channelName)
    }

    private val connectionListener = object : ConnectionEventListener {
        override fun onConnectionStateChange(change: ConnectionStateChange) {
            _state.value = when (change.currentState) {
                com.pusher.client.connection.ConnectionState.CONNECTED -> ReverbConnectionState.CONNECTED
                com.pusher.client.connection.ConnectionState.CONNECTING -> ReverbConnectionState.CONNECTING
                com.pusher.client.connection.ConnectionState.RECONNECTING -> ReverbConnectionState.RECONNECTING
                com.pusher.client.connection.ConnectionState.DISCONNECTED -> ReverbConnectionState.DISCONNECTED
                com.pusher.client.connection.ConnectionState.DISCONNECTING -> ReverbConnectionState.DISCONNECTED
                else -> _state.value
            }
            Log.d("ReverbClient", "State: ${change.previousState} -> ${change.currentState}")
        }

        override fun onError(message: String?, code: String?, e: Exception?) {
            Log.w("ReverbClient", "Connection error code=$code message=$message", e)
            _state.value = ReverbConnectionState.FAILED
        }
    }

    /**
     * Custom authorizer: the Pusher SDK calls this when a channel needs auth.
     * We POST channel_name + socket_id + client_id to /api/broadcast/auth with
     * the user's bearer token if logged in. The backend allows guest auth for
     * the playback/listen-along channels documented in [README §11].
     */
    private val broadcastAuthorizer = Authorizer { channelName, socketId ->
        val form = FormBody.Builder()
            .add("channel_name", channelName)
            .add("socket_id", socketId)
            .add("client_id", deviceIdManager.clientId)
            .build()

        val requestBuilder = Request.Builder()
            .url(ReverbConfig.AUTH_URL)
            .post(form)
            .header("Accept", "application/json")
            .header("X-Client-ID", deviceIdManager.clientId)

        authRepository.token?.let { requestBuilder.header("Authorization", "Bearer $it") }

        try {
            okHttpClient.get().newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw AuthorizationFailureException("Broadcast auth failed: HTTP ${response.code}")
                }
                response.body?.string() ?: throw AuthorizationFailureException("Empty auth response")
            }
        } catch (e: AuthorizationFailureException) {
            throw e
        } catch (e: Exception) {
            throw AuthorizationFailureException("Broadcast auth request failed", e)
        }
    }
}
