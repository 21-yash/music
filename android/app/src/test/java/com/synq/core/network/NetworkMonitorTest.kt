package com.synq.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.synq.features.music.jam.JamSessionManager
import com.synq.workers.SyncManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        val defaultNet = mockk<Network>()
        val defaultCaps = createCapabilities(internet = true, validated = true)
        every { connectivityManager.activeNetwork } returns defaultNet
        every { connectivityManager.getNetworkCapabilities(defaultNet) } returns defaultCaps
    }

    private fun createCapabilities(internet: Boolean, validated: Boolean): NetworkCapabilities {
        val caps = mockk<NetworkCapabilities>(relaxed = true)
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
    fun testCapabilitiesWithoutValidatedMarksOnlineAndNoSync() = runTest {
        mockkObject(SyncManager)
        try {
            every { SyncManager.triggerOneTimeSync(any()) } returns Unit

            val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
            every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } returns Unit

            val monitor = NetworkMonitor(context, jamSessionManagerProvider, this)
            monitor.startMonitoring()

            val net = mockk<Network>()
            // Captive portal / VPN / Jio: has internet capability flag, but NOT validated
            val unvalidatedCaps = createCapabilities(internet = true, validated = false)

            callbackSlot.captured.onAvailable(net)
            callbackSlot.captured.onCapabilitiesChanged(net, unvalidatedCaps)
            runCurrent()

            assertTrue("Network must be marked online when NET_CAPABILITY_INTERNET is present even without VALIDATED", monitor.isOnline.value)
            verify(exactly = 0) { SyncManager.triggerOneTimeSync(any()) }
        } finally {
            unmockkObject(SyncManager)
        }
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

    @Test
    fun testWifiToCellularWithNoOnLostNotifiesOnce() = runTest {
        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } returns Unit

        val monitor = NetworkMonitor(context, jamSessionManagerProvider, this)
        monitor.startMonitoring()

        val wifiNet = mockk<Network>()
        val cellNet = mockk<Network>()
        val wifiCaps = createCapabilities(internet = true, validated = true)
        val cellCaps = createCapabilities(internet = true, validated = true)

        every { connectivityManager.getNetworkCapabilities(wifiNet) } returns wifiCaps
        every { connectivityManager.getNetworkCapabilities(cellNet) } returns cellCaps

        // Initial setup on Wi-Fi
        callbackSlot.captured.onAvailable(wifiNet)
        callbackSlot.captured.onCapabilitiesChanged(wifiNet, wifiCaps)
        runCurrent()

        monitor.setJamActive(true)

        // Handover to Cellular without any onLost call for wifiNet
        callbackSlot.captured.onAvailable(cellNet)
        callbackSlot.captured.onCapabilitiesChanged(cellNet, cellCaps)
        runCurrent()

        assertEquals(1, providerGetCallCount)
        verify(exactly = 1) { mockJamSessionManager.onNetworkRestored() }
    }

    @Test
    fun testOnLostArrivingAfterOnAvailableDoesNotNotifySecondTime() = runTest {
        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } returns Unit

        val monitor = NetworkMonitor(context, jamSessionManagerProvider, this)
        monitor.startMonitoring()

        val wifiNet = mockk<Network>()
        val cellNet = mockk<Network>()
        val wifiCaps = createCapabilities(internet = true, validated = true)
        val cellCaps = createCapabilities(internet = true, validated = true)

        every { connectivityManager.getNetworkCapabilities(wifiNet) } returns wifiCaps
        every { connectivityManager.getNetworkCapabilities(cellNet) } returns cellCaps

        // Initial setup on Wi-Fi
        callbackSlot.captured.onAvailable(wifiNet)
        callbackSlot.captured.onCapabilitiesChanged(wifiNet, wifiCaps)
        runCurrent()

        monitor.setJamActive(true)

        // Handover: cell arrives first
        callbackSlot.captured.onAvailable(cellNet)
        callbackSlot.captured.onCapabilitiesChanged(cellNet, cellCaps)
        runCurrent()

        verify(exactly = 1) { mockJamSessionManager.onNetworkRestored() }

        // Delayed onLost for old Wi-Fi arrives AFTER cellular was already established
        callbackSlot.captured.onLost(wifiNet)
        runCurrent()

        // Must still be exactly 1, not 2
        verify(exactly = 1) { mockJamSessionManager.onNetworkRestored() }
        assertTrue("Should still be online via cellular", monitor.isOnline.value)
    }

    @Test
    fun testCapabilityFlappingOnSameNetworkDoesNotNotify() = runTest {
        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } returns Unit

        val monitor = NetworkMonitor(context, jamSessionManagerProvider, this)
        monitor.startMonitoring()

        val net = mockk<Network>()
        val caps1 = createCapabilities(internet = true, validated = true)
        val caps2 = createCapabilities(internet = true, validated = true)
        val caps3 = createCapabilities(internet = true, validated = true)

        every { connectivityManager.getNetworkCapabilities(net) } returns caps1

        // Initial setup
        callbackSlot.captured.onAvailable(net)
        callbackSlot.captured.onCapabilitiesChanged(net, caps1)
        runCurrent()

        monitor.setJamActive(true)

        // Signal strength / flapping on the SAME network
        callbackSlot.captured.onCapabilitiesChanged(net, caps2)
        runCurrent()
        callbackSlot.captured.onCapabilitiesChanged(net, caps3)
        runCurrent()

        verify(exactly = 0) { mockJamSessionManager.onNetworkRestored() }
    }

    @Test
    fun testBurstOfEventsProcessedInStrictArrivalOrder() = runTest {
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

        // Initial setup on net1
        callbackSlot.captured.onAvailable(net1)
        callbackSlot.captured.onCapabilitiesChanged(net1, caps1)
        runCurrent()

        monitor.setJamActive(true)

        // Rapid burst: net1 lost, then net2 available, then net2 capabilities changed
        callbackSlot.captured.onLost(net1)
        callbackSlot.captured.onAvailable(net2)
        callbackSlot.captured.onCapabilitiesChanged(net2, caps2)

        // Run all coroutine tasks
        runCurrent()

        assertTrue("Final state must be online after net2 arrived", monitor.isOnline.value)
        verify(exactly = 1) { mockJamSessionManager.onNetworkRestored() }
    }

    @Test
    fun testStartOfflineThenValidatedNetworkTriggersOneTimeSyncOnce() = runTest {
        mockkObject(SyncManager)
        try {
            every { SyncManager.triggerOneTimeSync(any()) } returns Unit

            val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
            every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } returns Unit
            every { connectivityManager.activeNetwork } returns null

            // Start monitor offline
            val monitor = NetworkMonitor(context, jamSessionManagerProvider, this)
            assertFalse(monitor.isOnline.value)
            monitor.startMonitoring()

            val net = mockk<Network>()
            val caps = createCapabilities(internet = true, validated = true)
            every { connectivityManager.getNetworkCapabilities(net) } returns caps

            // First validated network callback arrives
            callbackSlot.captured.onAvailable(net)
            callbackSlot.captured.onCapabilitiesChanged(net, caps)
            runCurrent()

            assertTrue(monitor.isOnline.value)
            verify(exactly = 1) { SyncManager.triggerOneTimeSync(context) }
        } finally {
            unmockkObject(SyncManager)
        }
    }

    @Test
    fun testAppStartsOfflineThenNetworkWithoutValidatedAvailableMarksOnlineAndTriggersSyncOnce() = runTest {
        mockkObject(SyncManager)
        try {
            every { SyncManager.triggerOneTimeSync(any()) } returns Unit

            val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
            every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } returns Unit
            every { connectivityManager.activeNetwork } returns null

            // Start monitor offline
            val monitor = NetworkMonitor(context, jamSessionManagerProvider, this)
            assertFalse(monitor.isOnline.value)
            monitor.startMonitoring()

            val net = mockk<Network>()
            val caps = createCapabilities(internet = true, validated = false)
            every { connectivityManager.getNetworkCapabilities(net) } returns caps

            // Network callback arrives with internet=true, validated=false
            callbackSlot.captured.onAvailable(net)
            callbackSlot.captured.onCapabilitiesChanged(net, caps)
            runCurrent()

            assertTrue(monitor.isOnline.value)
            verify(exactly = 1) { SyncManager.triggerOneTimeSync(context) }
        } finally {
            unmockkObject(SyncManager)
        }
    }

    @Test
    fun testAppStartsOnlineDoesNotTriggerSync() = runTest {
        mockkObject(SyncManager)
        try {
            every { SyncManager.triggerOneTimeSync(any()) } returns Unit

            val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
            every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } returns Unit

            // App starts online (activeNetwork configured in @Before setup)
            val monitor = NetworkMonitor(context, jamSessionManagerProvider, this)
            assertTrue(monitor.isOnline.value)
            monitor.startMonitoring()

            val net = mockk<Network>()
            val caps = createCapabilities(internet = true, validated = true)
            every { connectivityManager.getNetworkCapabilities(net) } returns caps

            callbackSlot.captured.onAvailable(net)
            callbackSlot.captured.onCapabilitiesChanged(net, caps)
            runCurrent()

            assertTrue(monitor.isOnline.value)
            verify(exactly = 0) { SyncManager.triggerOneTimeSync(any()) }
        } finally {
            unmockkObject(SyncManager)
        }
    }

    @Test
    fun testValidatedFlapOnSameNetworkWhileOnlineDoesNotTriggerSyncAndStaysOnline() = runTest {
        mockkObject(SyncManager)
        try {
            every { SyncManager.triggerOneTimeSync(any()) } returns Unit

            val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
            every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } returns Unit

            val monitor = NetworkMonitor(context, jamSessionManagerProvider, this)
            monitor.startMonitoring()

            val net = mockk<Network>()
            val capsValidated = createCapabilities(internet = true, validated = true)
            val capsUnvalidated = createCapabilities(internet = true, validated = false)
            every { connectivityManager.getNetworkCapabilities(net) } returns capsValidated

            // Establish net with validated=true
            callbackSlot.captured.onAvailable(net)
            callbackSlot.captured.onCapabilitiesChanged(net, capsValidated)
            runCurrent()

            assertTrue(monitor.isOnline.value)

            // Flap: validated drops to false on same network
            callbackSlot.captured.onCapabilitiesChanged(net, capsUnvalidated)
            runCurrent()

            assertTrue("Must stay online when validated drops but internet remains", monitor.isOnline.value)
            verify(exactly = 0) { SyncManager.triggerOneTimeSync(any()) }

            // Flap: validated returns to true on same network
            callbackSlot.captured.onCapabilitiesChanged(net, capsValidated)
            runCurrent()

            assertTrue("Must stay online when validated returns", monitor.isOnline.value)
            verify(exactly = 0) { SyncManager.triggerOneTimeSync(any()) }
        } finally {
            unmockkObject(SyncManager)
        }
    }
}
