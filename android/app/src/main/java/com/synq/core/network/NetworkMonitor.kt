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

    private var wasOffline = !hasValidInternetConnection()
    private var lastDefaultNetwork: Network? = null
    private var lastValidated: Boolean = false
    private var isInitialCallback = true

    @Volatile
    private var isJamActive: Boolean = false

    fun setJamActive(active: Boolean) {
        isJamActive = active
    }

    fun isJamActive(): Boolean = isJamActive

    private fun checkInitialConnectivity(): Boolean {
        return hasValidInternetConnection()
    }

    private fun hasValidInternetConnection(): Boolean {
        val activeNetwork = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun startMonitoring() {
        connectivityManager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    val networkChanged = lastDefaultNetwork != null && lastDefaultNetwork != network
                    lastDefaultNetwork = network

                    if (isInitialCallback) {
                        return@launch
                    }

                    if (networkChanged || wasOffline) {
                        if (hasValidInternetConnection()) {
                            val previouslyOffline = !_isOnline.value || wasOffline
                            _isOnline.value = true
                            wasOffline = false
                            lastValidated = true
                            if (previouslyOffline) {
                                SyncManager.triggerOneTimeSync(context)
                            }
                            if (isJamActive) {
                                jamSessionManagerProvider.get().onNetworkRestored()
                            }
                        }
                    }
                }
            }

            override fun onLost(network: Network) {
                scope.launch {
                    isInitialCallback = false
                    if (lastDefaultNetwork == network) {
                        lastDefaultNetwork = null
                        lastValidated = false
                    }
                    if (hasValidInternetConnection()) {
                        _isOnline.value = true
                        wasOffline = false
                        lastValidated = true
                        if (isJamActive) {
                            jamSessionManagerProvider.get().onNetworkRestored()
                        }
                    } else {
                        wasOffline = true
                        lastValidated = false
                        _isOnline.value = false
                    }
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val isOnlineNow = hasInternet && isValidated

                scope.launch {
                    val networkChanged = lastDefaultNetwork != null && lastDefaultNetwork != network
                    lastDefaultNetwork = network

                    if (isInitialCallback) {
                        isInitialCallback = false
                        lastValidated = isOnlineNow
                        _isOnline.value = isOnlineNow
                        wasOffline = !isOnlineNow
                        return@launch
                    }

                    if (isOnlineNow) {
                        val transitionedToOnline = !_isOnline.value || wasOffline || (!lastValidated && isValidated)
                        _isOnline.value = true
                        wasOffline = false
                        lastValidated = true

                        if (transitionedToOnline || networkChanged) {
                            if (transitionedToOnline) {
                                SyncManager.triggerOneTimeSync(context)
                            }
                            if (isJamActive) {
                                jamSessionManagerProvider.get().onNetworkRestored()
                            }
                        }
                    } else if (!hasInternet) {
                        lastValidated = false
                        wasOffline = true
                        _isOnline.value = false
                    }
                }
            }
        })
    }
}
