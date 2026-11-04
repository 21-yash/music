package com.synq.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.synq.features.music.jam.JamSessionManager
import com.synq.features.music.jam.safeLogI
import com.synq.workers.SyncManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class NetworkMonitor(
    @ApplicationContext private val context: Context,
    private val jamSessionManagerProvider: Provider<JamSessionManager>,
    coroutineScope: CoroutineScope? = null
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        jamSessionManagerProvider: Provider<JamSessionManager>
    ) : this(context, jamSessionManagerProvider, null)

    private val scope = coroutineScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(hasValidInternetConnection())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val eventChannel = Channel<NetworkEvent>(Channel.UNLIMITED)
    private val isDraining = AtomicBoolean(false)

    internal var lastNotifiedValidatedNetwork: Network? = null
    private var currentNetwork: Network? = null
    private var isInitialCallback = true
    internal var isMonitoring = false

    @Volatile
    private var isJamActive: Boolean = false
    private val networkTransports = mutableMapOf<Network, String>()

    fun setJamActive(active: Boolean) {
        isJamActive = active
    }

    fun isJamActive(): Boolean = isJamActive

    fun describeActiveNetwork(): String {
        val cm = connectivityManager ?: return "NONE"
        val active = cm.activeNetwork ?: return "NONE"
        val caps = cm.getNetworkCapabilities(active) ?: return "NONE"
        return getTransportName(caps)
    }

    internal fun getTransportName(caps: NetworkCapabilities?): String {
        if (caps == null) return "UNKNOWN"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "BLUETOOTH"
            else -> "OTHER"
        }
    }

    private fun getShortId(network: Network): String {
        val str = network.toString()
        return if (str.length > 12) str.takeLast(8) else str
    }

    private fun decideValidatedAction(network: Network): String {
        if (isInitialCallback) return "ignored"
        val wasOffline = !_isOnline.value
        val networkChanged = network != lastNotifiedValidatedNetwork
        return if (networkChanged || wasOffline) {
            if (isJamActive) "handover started" else "new active network"
        } else {
            "ignored"
        }
    }

    private fun hasValidInternetConnection(): Boolean {
        val activeNetwork = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        connectivityManager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                enqueueEvent(NetworkEvent.Available(network))
            }

            override fun onLost(network: Network) {
                enqueueEvent(NetworkEvent.Lost(network))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                enqueueEvent(NetworkEvent.CapabilitiesChanged(network, networkCapabilities))
            }
        })
    }

    private fun enqueueEvent(event: NetworkEvent) {
        eventChannel.trySend(event)
        drainEvents()
    }

    private fun drainEvents() {
        if (!isDraining.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (true) {
                    val event = eventChannel.tryReceive().getOrNull() ?: break
                    processEvent(event)
                }
            } finally {
                isDraining.set(false)
                if (!eventChannel.isEmpty) {
                    drainEvents()
                }
            }
        }
    }

    private fun processEvent(event: NetworkEvent) {
        when (event) {
            is NetworkEvent.Available -> {
                val caps = connectivityManager?.getNetworkCapabilities(event.network)
                val isValidated = caps != null &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val transport = getTransportName(caps)
                networkTransports[event.network] = transport
                val shortId = getShortId(event.network)

                if (isValidated) {
                    val decision = decideValidatedAction(event.network)
                    safeLogI("NetworkMonitor", "Available: id=$shortId, validated=true, transport=$transport -> $decision")
                    handleValidatedNetwork(event.network)
                } else {
                    safeLogI("NetworkMonitor", "Available: id=$shortId, validated=false, transport=$transport -> queued")
                    currentNetwork = event.network
                }
            }
            is NetworkEvent.CapabilitiesChanged -> {
                val hasInternet = event.capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = event.capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val isOnlineNow = hasInternet && isValidated
                val transport = getTransportName(event.capabilities)
                networkTransports[event.network] = transport
                val shortId = getShortId(event.network)

                if (isOnlineNow) {
                    val decision = decideValidatedAction(event.network)
                    safeLogI("NetworkMonitor", "CapabilitiesChanged: id=$shortId, validated=true, transport=$transport -> $decision")
                    handleValidatedNetwork(event.network)
                } else {
                    safeLogI("NetworkMonitor", "CapabilitiesChanged: id=$shortId, validated=false, transport=$transport -> ignored")
                    if (currentNetwork == event.network || lastNotifiedValidatedNetwork == event.network) {
                        _isOnline.value = false
                        lastNotifiedValidatedNetwork = null
                    }
                }
            }
            is NetworkEvent.Lost -> {
                val shortId = getShortId(event.network)
                val transport = networkTransports.remove(event.network) ?: "UNKNOWN"
                safeLogI("NetworkMonitor", "Lost: id=$shortId, validated=false, transport=$transport -> ignored")
                handleNetworkLost(event.network)
            }
        }
    }

    private fun handleValidatedNetwork(network: Network) {
        currentNetwork = network
        val wasOffline = !_isOnline.value
        _isOnline.value = true

        if (isInitialCallback) {
            isInitialCallback = false
            lastNotifiedValidatedNetwork = network
            return
        }

        val networkChanged = network != lastNotifiedValidatedNetwork
        if (networkChanged || wasOffline) {
            lastNotifiedValidatedNetwork = network
            if (wasOffline) {
                try {
                    SyncManager.triggerOneTimeSync(context)
                } catch (_: Throwable) {}
            }
            if (isJamActive) {
                jamSessionManagerProvider.get().onNetworkRestored()
            }
        }
    }

    private fun handleNetworkLost(network: Network) {
        if (isInitialCallback) {
            isInitialCallback = false
        }
        if (lastNotifiedValidatedNetwork == network) {
            lastNotifiedValidatedNetwork = null
        }
        if (currentNetwork == network) {
            currentNetwork = null
        }
        _isOnline.value = (lastNotifiedValidatedNetwork != null) || hasValidInternetConnection()
    }

    private sealed interface NetworkEvent {
        data class Available(val network: Network) : NetworkEvent
        data class Lost(val network: Network) : NetworkEvent
        data class CapabilitiesChanged(val network: Network, val capabilities: NetworkCapabilities) : NetworkEvent
    }
}
