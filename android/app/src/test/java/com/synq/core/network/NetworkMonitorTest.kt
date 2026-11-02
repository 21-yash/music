package com.synq.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.synq.features.music.jam.JamSessionManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkMonitorTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var mockJamSessionManager: JamSessionManager
    private lateinit var jamSessionManagerProvider: Provider<JamSessionManager>
    private var providerGetCallCount = 0

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)
        mockJamSessionManager = mockk(relaxed = true)
        providerGetCallCount = 0

        jamSessionManagerProvider = Provider {
            providerGetCallCount++
            mockJamSessionManager
        }

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
    }

    private fun createCapabilities(internet: Boolean, validated: Boolean): NetworkCapabilities {
        val caps = mockk<NetworkCapabilities>()
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns internet
        every { caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns validated
        return caps
    }

    @Test
    fun testInitialRegistrationDoesNotCallJamSessionManager() = runTest {
        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } returns Unit

        val monitor = NetworkMonitor(context, jamSessionManagerProvider, this)
        monitor.startMonitoring()

        val mockNetwork = mockk<Network>()
        val caps = createCapabilities(internet = true, validated = true)
        every { connectivityManager.getNetworkCapabilities(mockNetwork) } returns caps

        // Simulate Android immediate initial callbacks on registration
        callbackSlot.captured.onAvailable(mockNetwork)
        callbackSlot.captured.onCapabilitiesChanged(mockNetwork, caps)
        runCurrent()

        // Verify JamSessionManager was never constructed or called
        assertTrue(providerGetCallCount == 0)
        verify(exactly = 0) { mockJamSessionManager.onNetworkRestored() }
    }

    @Test
    fun testNetworkChangeWhenJamActiveCallsOnNetworkRestored() = runTest {
        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } returns Unit

        val monitor = NetworkMonitor(context, jamSessionManagerProvider, this)
        monitor.startMonitoring()

        val net1 = mockk<Network>()
        val net2 = mockk<Network>()
        val caps1 = createCapabilities(internet = true, validated = true)
        val caps2 = createCapabilities(internet = true, validated = true)

        every { connectivityManager.getNetworkCapabilities(net1) } returns caps1
        every { connectivityManager.getNetworkCapabilities(net2) } returns caps2
        every { connectivityManager.activeNetwork } returns net2

        // Initial callback
        callbackSlot.captured.onAvailable(net1)
        callbackSlot.captured.onCapabilitiesChanged(net1, caps1)
        runCurrent()

        // Activate Jam
        monitor.setJamActive(true)

        // Switch to net2 (e.g. Wi-Fi to cellular)
        callbackSlot.captured.onAvailable(net2)
        callbackSlot.captured.onCapabilitiesChanged(net2, caps2)
        runCurrent()

        assertTrue("JamSessionManager must be called on network change when active", providerGetCallCount > 0)
        verify(atLeast = 1) { mockJamSessionManager.onNetworkRestored() }
    }

    @Test
    fun testCapabilitiesWithoutValidatedDoesNotMarkOnline() = runTest {
        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } returns Unit

        val monitor = NetworkMonitor(context, jamSessionManagerProvider, this)
        monitor.startMonitoring()

        val net = mockk<Network>()
        // Captive portal: has internet capability flag, but NOT validated
        val unvalidatedCaps = createCapabilities(internet = true, validated = false)

        callbackSlot.captured.onAvailable(net)
        callbackSlot.captured.onCapabilitiesChanged(net, unvalidatedCaps)
        runCurrent()

        assertFalse("Network must not be marked online unless NET_CAPABILITY_VALIDATED is present", monitor.isOnline.value)
    }

    @Test
    fun testWhenJamInactiveNetworkChangesDoNotConstructJamSessionManager() = runTest {
        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } returns Unit

        val monitor = NetworkMonitor(context, jamSessionManagerProvider, this)
        monitor.setJamActive(false)
        monitor.startMonitoring()

        val net1 = mockk<Network>()
        val net2 = mockk<Network>()
        val caps = createCapabilities(internet = true, validated = true)
        every { connectivityManager.getNetworkCapabilities(net2) } returns caps
        every { connectivityManager.activeNetwork } returns net2

        callbackSlot.captured.onAvailable(net1)
        callbackSlot.captured.onCapabilitiesChanged(net1, caps)
        callbackSlot.captured.onAvailable(net2)
        callbackSlot.captured.onCapabilitiesChanged(net2, caps)
        runCurrent()

        assertTrue("Provider must not be called when Jam is inactive", providerGetCallCount == 0)
        verify(exactly = 0) { mockJamSessionManager.onNetworkRestored() }
    }
}

