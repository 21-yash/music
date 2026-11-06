package com.synq.features.music.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * SingleFlightExtractor coordinates stream extraction requests.
 * - In-flight map ensures concurrent requests for the same videoId join the running extraction.
 * - Concurrency of PREFETCH extractions is limited to 1 (via Semaphore).
 * - User-initiated PLAY requests bypass the semaphore so they never wait behind prefetches.
 */
class SingleFlightExtractor(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val extractFn: suspend (videoId: String, reason: String) -> String?
) {
    constructor(
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        extractFn: suspend (videoId: String) -> String?
    ) : this(scope, { videoId, _ -> extractFn(videoId) })

    private val inFlight = ConcurrentHashMap<String, Deferred<String?>>()
    private val prefetchSemaphore = Semaphore(1)

    val inFlightCount: Int
        get() = inFlight.size

    private fun getOrCreateDeferred(videoId: String, reason: String): Deferred<String?> {
        inFlight[videoId]?.let { return it }

        synchronized(inFlight) {
            val existing = inFlight[videoId]
            if (existing != null) return existing

            lateinit var thatDeferred: Deferred<String?>
            thatDeferred = scope.async(start = CoroutineStart.LAZY) {
                try {
                    extractFn(videoId, reason)
                } finally {
                    inFlight.remove(videoId, thatDeferred)
                }
            }
            inFlight[videoId] = thatDeferred
            thatDeferred.start()
            return thatDeferred
        }
    }

    suspend fun extract(videoId: String, reason: String = "play"): String? {
        if (reason == "prefetch") {
            // Fast path: if already in flight, join without taking the semaphore
            inFlight[videoId]?.let { return it.await() }

            return prefetchSemaphore.withPermit {
                // Double check after acquiring permit
                inFlight[videoId]?.let { return@withPermit it.await() }

                val deferred = getOrCreateDeferred(videoId, reason)
                deferred.await()
            }
        } else {
            // User-initiated play (or warmup) bypasses the prefetch semaphore
            val deferred = getOrCreateDeferred(videoId, reason)
            return deferred.await()
        }
    }
}
