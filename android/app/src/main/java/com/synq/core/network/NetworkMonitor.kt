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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jamSessionManagerProvider: Provider<JamSessionManager>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkInitialConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var wasOffline = false

    private fun checkInitialConnectivity(): Boolean {
        val activeNetwork = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun hasValidInternetConnection(): Boolean {
        val activeNetwork = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun startMonitoring() {
        connectivityManager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    val previouslyOffline = !_isOnline.value || wasOffline
                    _isOnline.value = true
                    wasOffline = false
                    if (previouslyOffline) {
                        // On network restored: trigger library sync
                        SyncManager.triggerOneTimeSync(context)
                    }
                    // On default-network change/restore (Wi-Fi <-> 4G), force socket to reconnect
                    jamSessionManagerProvider.get().onNetworkRestored()
                }
            }

            override fun onLost(network: Network) {
                scope.launch {
                    // Check if another validated network is still available before marking offline
                    if (hasValidInternetConnection()) {
                        _isOnline.value = true
                        wasOffline = false
                        jamSessionManagerProvider.get().onNetworkRestored()
                    } else {
                        wasOffline = true
                        _isOnline.value = false
                    }
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                scope.launch {
                    if (hasInternet && !_isOnline.value) {
                        _isOnline.value = true
                        wasOffline = false
                        SyncManager.triggerOneTimeSync(context)
                        jamSessionManagerProvider.get().onNetworkRestored()
                    }
                }
            }
        })
    }
}
