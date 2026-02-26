package nl.stoux.tfw.service.playback.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network connection types.
 */
enum class NetworkType {
    /** Connected via WiFi */
    WIFI,
    /** Connected via cellular data */
    CELLULAR,
    /** No network connection */
    OFFLINE,
}

/**
 * Monitors network connectivity state for download decisions.
 */
@Singleton
class NetworkStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkState = MutableStateFlow(getCurrentNetworkType())

    /** Current network type as a reactive flow */
    val networkState: StateFlow<NetworkType> = _networkState

    /** Whether the device is currently on cellular data */
    val isCellular: Flow<Boolean> = networkState.map { it == NetworkType.CELLULAR }.distinctUntilChanged()

    /** Whether the device is currently offline */
    val isOffline: Flow<Boolean> = networkState.map { it == NetworkType.OFFLINE }.distinctUntilChanged()

    /** Whether the device is in airplane mode */
    val isAirplaneMode: Flow<Boolean> = callbackFlow {
        // Initial value
        trySend(checkAirplaneMode())

        // We can't easily observe airplane mode changes, so we rely on network callbacks
        // When network changes, re-check airplane mode
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(checkAirplaneMode())
            }

            override fun onLost(network: Network) {
                trySend(checkAirplaneMode())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    init {
        // Register network callback to update state
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _networkState.value = getCurrentNetworkType()
            }

            override fun onLost(network: Network) {
                _networkState.value = getCurrentNetworkType()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                _networkState.value = getNetworkType(networkCapabilities)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
    }

    private fun getCurrentNetworkType(): NetworkType {
        val activeNetwork = connectivityManager.activeNetwork
            ?: return NetworkType.OFFLINE
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return NetworkType.OFFLINE
        return getNetworkType(capabilities)
    }

    private fun getNetworkType(capabilities: NetworkCapabilities): NetworkType {
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            else -> NetworkType.OFFLINE
        }
    }

    @Suppress("DEPRECATION")
    private fun checkAirplaneMode(): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0
        ) != 0
    }
}
