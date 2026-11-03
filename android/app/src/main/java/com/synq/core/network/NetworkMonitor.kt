package com.synq.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.synq.features.music.jam.JamSessionManager
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

    fun setJamActive(active: Boolean) {
        isJamActive = active
    }

    fun isJamActive(): Boolean = isJamActive

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
                if (isValidated) {
                    handleValidatedNetwork(event.network)
                } else {
                    currentNetwork = event.network
                }
            }
            is NetworkEvent.CapabilitiesChanged -> {
                val hasInternet = event.capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = event.capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val isOnlineNow = hasInternet && isValidated
                if (isOnlineNow) {
                    handleValidatedNetwork(event.network)
                } else {
                    if (currentNetwork == event.network || lastNotifiedValidatedNetwork == event.network) {
                        _isOnline.value = false
                        lastNotifiedValidatedNetwork = null
                    }
                }
            }
            is NetworkEvent.Lost -> {
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
