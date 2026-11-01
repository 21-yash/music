package com.synq.features.music.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SingleFlightExtractorTest {

    @Test
    fun testConcurrentCalls_sameId_runsLambdaOnce() = runTest {
        val callCount = AtomicInteger(0)
        val extractor = SingleFlightExtractor(this) { videoId, reason ->
            callCount.incrementAndGet()
            delay(100)
            "https://stream.example/$videoId"
        }

        val d1 = async { extractor.extract("vid1", "play") }
        val d2 = async { extractor.extract("vid1", "play") }
        val d3 = async { extractor.extract("vid1", "play") }

        val r1 = d1.await()
        val r2 = d2.await()
        val r3 = d3.await()

        assertEquals("https://stream.example/vid1", r1)
        assertEquals("https://stream.example/vid1", r2)
        assertEquals("https://stream.example/vid1", r3)
        assertEquals(1, callCount.get())
    }

    @Test
    fun testDifferentIds_runSeparately() = runTest {
        val callCount = AtomicInteger(0)
        val extractor = SingleFlightExtractor(this) { videoId, _ ->
            callCount.incrementAndGet()
            "https://stream.example/$videoId"
        }

        val r1 = extractor.extract("vid1", "play")
        val r2 = extractor.extract("vid2", "play")

        assertEquals("https://stream.example/vid1", r1)
        assertEquals("https://stream.example/vid2", r2)
        assertEquals(2, callCount.get())
    }

    @Test
    fun testFailure_isNotCached() = runTest {
        val extractorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val callCount = AtomicInteger(0)
        val extractor = SingleFlightExtractor(extractorScope) { videoId, _ ->
            val count = callCount.incrementAndGet()
            if (count == 1) {
                throw IllegalStateException("Extraction network error")
            }
            "https://stream.example/$videoId"
        }

        try {
            extractor.extract("vid1", "play")
            fail("Expected exception on first call")
        } catch (_: IllegalStateException) {
            // expected
        }

        val r2 = extractor.extract("vid1", "play")
        assertEquals("https://stream.example/vid1", r2)
        assertEquals(2, callCount.get())
    }

    @Test
    fun testNullResult_isNotCached() = runTest {
        val callCount = AtomicInteger(0)
        val extractor = SingleFlightExtractor(this) { videoId, _ ->
            val count = callCount.incrementAndGet()
            if (count == 1) {
                null
            } else {
                "https://stream.example/$videoId"
            }
        }

        val r1 = extractor.extract("vid1", "play")
        assertEquals(null, r1)

        val r2 = extractor.extract("vid1", "play")
        assertEquals("https://stream.example/vid1", r2)
        assertEquals(2, callCount.get())
    }

    @Test
    fun testCancellation_ofOneWaiter_doesNotBreakOther() = runTest {
        val extractorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val extractor = SingleFlightExtractor(extractorScope) { videoId, _ ->
            delay(150)
            "https://stream.example/$videoId"
        }

        val waiter1 = launch {
            extractor.extract("vid1", "prefetch")
        }

        val waiter2 = async {
            delay(20) // ensure waiter1 started first
            extractor.extract("vid1", "play")
        }

        delay(50)
        waiter1.cancel(CancellationException("Waiter 1 cancelled"))

        val result2 = waiter2.await()
        assertEquals("https://stream.example/vid1", result2)
    }

    @Test
    fun testPrefetch_limitedToSingleConcurrency() = runTest {
        val currentConcurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        val extractor = SingleFlightExtractor(this) { videoId, _ ->
            val cur = currentConcurrent.incrementAndGet()
            maxConcurrent.updateAndGet { maxOf(it, cur) }
            delay(50)
            currentConcurrent.decrementAndGet()
            "https://stream.example/$videoId"
        }

        val p1 = async { extractor.extract("p1", "prefetch") }
        val p2 = async { extractor.extract("p2", "prefetch") }
        val p3 = async { extractor.extract("p3", "prefetch") }

        p1.await()
        p2.await()
        p3.await()

        assertEquals(1, maxConcurrent.get())
    }

    @Test
    fun testPlay_bypassesPrefetchSemaphore() = runTest {
        val currentConcurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        val extractor = SingleFlightExtractor(this) { videoId, reason ->
            val cur = currentConcurrent.incrementAndGet()
            maxConcurrent.updateAndGet { maxOf(it, cur) }
            if (reason == "prefetch") {
                delay(200)
            } else {
                delay(50)
            }
            currentConcurrent.decrementAndGet()
            "https://stream.example/$videoId"
        }

        val p1 = async { extractor.extract("p1", "prefetch") }
        delay(20) // let p1 acquire semaphore and enter delay

        // Play for a different track should start immediately without waiting for p1
        val play1 = async { extractor.extract("play1", "play") }

        val playRes = play1.await()
        assertEquals("https://stream.example/play1", playRes)
        assertTrue(
            "Play should run concurrently with prefetch (maxConcurrent >= 2)",
            maxConcurrent.get() >= 2
        )

        p1.await()
    }

    @Test
    fun testPlay_joinsInFlightPrefetch() = runTest {
        val callCount = AtomicInteger(0)
        val extractor = SingleFlightExtractor(this) { videoId, _ ->
            callCount.incrementAndGet()
            delay(100)
            "https://stream.example/$videoId"
        }

        val prefetchDeferred = async { extractor.extract("songA", "prefetch") }
        delay(20)
        val playDeferred = async { extractor.extract("songA", "play") }

        val prefetchRes = prefetchDeferred.await()
        val playRes = playDeferred.await()

        assertEquals("https://stream.example/songA", prefetchRes)
        assertEquals("https://stream.example/songA", playRes)
        assertEquals(1, callCount.get())
    }
}

